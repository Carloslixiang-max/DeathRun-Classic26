package flow;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;
import pl.mrstudios.deathrun.arena.*;
import pl.mrstudios.deathrun.arena.listener.ArenaCheckpointReachedListener;
import pl.mrstudios.deathrun.arena.win.WinMapManager;
import pl.mrstudios.deathrun.classic.death.*;
import pl.mrstudios.deathrun.classic.playtest.PlaytestTraceService;
import pl.mrstudios.deathrun.classic.strafe.ClassicStrafeService;
import pl.mrstudios.deathrun.classic.vote.ClassicVoteService;
import pl.mrstudios.deathrun.config.Configuration;
import pl.mrstudios.deathrun.config.impl.*;
import pl.mrstudios.deathrun.plugin.Entrypoint;
import pl.mrstudios.deathrun.reward.RewardService;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static pl.mrstudios.deathrun.api.arena.enums.GameState.*;
import static pl.mrstudios.deathrun.api.arena.user.enums.Role.*;

/** CI-only: real Paper registries, blocks and production services; simulated Player interfaces.
 * No network clients, physics, rendered HUD, or human acceptance are claimed.
 * This separate plugin is excluded from the production JAR and release artifact.
 */
public final class FlowSmokePlugin extends JavaPlugin {
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length != 1 || !Bukkit.getOnlinePlayers().isEmpty())
            return false;
        try {
            var deathrun = (Entrypoint) Bukkit.getPluginManager().getPlugin("DeathRun");
            var runtime = deathrun.getArenaManager().runtimeByMapId(args[0]);
            if (runtime == null || !runtime.arena().getUsers().isEmpty()) throw new AssertionError("map not idle");
            new Simulation(this, runtime.map()).run();
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "[DR-FLOW] FAIL", failure);
        }
        return true;
    }

    static final class Simulation {
        final FlowSmokePlugin owner;
        final Server real = Bukkit.getServer();
        final Map<UUID, Actor> actors = new LinkedHashMap<>();
        final List<Runnable> timers = new ArrayList<>();
        final MapConfiguration.MapDefinition map;
        final Arena arena;
        final ArenaManager manager;
        final ArenaServiceRunnable round;
        final Plugin plugin;
        final Server server;
        final Configuration configuration;
        final WinMapManager wins;
        final RewardService rewards;
        final PlaytestTraceService trace;
        final ArenaCheckpointReachedListener checkpoints;
        final DeathRunDeathService deaths;
        final Path data;
        int checks;

        Simulation(FlowSmokePlugin owner, MapConfiguration.MapDefinition map) throws Exception {
            this.owner = owner; this.map = map;
            data = Files.createTempDirectory(owner.getDataFolder().toPath().toAbsolutePath().getParent(), "flow-smoke-");
            Logger quiet = Logger.getLogger("flow-smoke-isolated"); quiet.setUseParentHandlers(false);
            var pluginManager = proxy(PluginManager.class, (method, args) -> {
                if (method.getName().equals("callEvent")) return null; // isolated event bus; no live listeners
                return invoke(real.getPluginManager(), method, args);
            });
            var scheduler = proxy(BukkitScheduler.class, (method, args) -> {
                if (method.getName().startsWith("runTask")) {
                    if (args.length > 1 && args[1] instanceof Runnable task) timers.add(task);
                    return proxy(BukkitTask.class, (m,a) -> defaultValue(m.getReturnType()));
                }
                return invoke(real.getScheduler(), method, args);
            });
            server = proxy(Server.class, (method, args) -> switch (method.getName()) {
                case "getPlayer", "getPlayerExact" -> {
                    Actor actor = actors.values().stream().filter(a -> a.id.equals(args[0]) || a.name.equals(args[0])).findFirst().orElse(null);
                    yield actor == null || !actor.online ? null : actor.player;
                }
                case "getOnlinePlayers" -> actors.values().stream().filter(a -> a.online).map(a -> a.player).toList();
                case "getPluginManager" -> pluginManager;
                case "getScheduler" -> scheduler;
                default -> invoke(real, method, args);
            });
            plugin = proxy(Plugin.class, (method,args) -> switch (method.getName()) {
                case "getServer" -> server;
                case "getDataFolder" -> data.toFile();
                case "getLogger" -> quiet;
                case "getName" -> "DeathRun";
                default -> invoke(owner, method, args);
            });
            var settings = new PluginConfiguration();
            var language = new LanguageConfiguration(); language.arenaScoreboardEnabled = false;
            var maps = new MapConfiguration(); maps.maps = new ArrayList<>(List.of(map));
            configuration = new Configuration(settings, language, maps);
            wins = new WinMapManager() {
                @Override public void giveWinMap(Player player, java.awt.image.BufferedImage image, int place, int time) {}
                @Override public void giveLoseMap(Player player, java.awt.image.BufferedImage image, int count) {}
            }; // result artwork needs actual clients; gameplay, score and exit inventory remain real
            rewards = new RewardService(plugin, configuration);
            arena = new Arena(map.id);
            manager = new ArenaManager(plugin, server, configuration, wins, rewards);
            round = new ArenaServiceRunnable(arena, map, manager, wins, rewards, plugin, server, configuration);
            @SuppressWarnings("unchecked") var runtimes = (Map<String,ArenaManager.ArenaRuntime>) field(manager, "runtimesByMapId");
            runtimes.put(map.id, new ArenaManager.ArenaRuntime(map.id, map, arena, round));
            trace = new PlaytestTraceService(plugin, manager);
            checkpoints = new ArenaCheckpointReachedListener(manager, plugin, server, configuration, wins, rewards, trace);
            deaths = new DeathRunDeathService(manager, plugin, server, configuration, wins);
        }

        void run() throws Exception {
            Field global = Bukkit.class.getDeclaredField("server"); global.setAccessible(true);
            // Main-thread console command only. Always restore the real singleton before returning.
            Map<Location, org.bukkit.block.data.BlockData> barriers = new LinkedHashMap<>();
            for (Location block : map.arenaStartBarrierBlocks) barriers.put(block, block.getBlock().getBlockData().clone());
            global.set(null, server);
            try {
                check(manager.isMapConfigured(map), "real map ready");
                check(map.name.equals("To Bee Or Not To Bee"), "English map name");
                // CI has already exercised exact courtyard migration and custom-location preservation.
                Actor first = actor();
                var vote = new ClassicVoteService(plugin, configuration, manager);
                vote.open(first.player);
                check(first.opened != null && ClassicVoteService.isClassicVoteInventory(first.opened), "vote opens");
                check(first.opened.getItem(10) != null && first.opened.getItem(16) != null, "map and Random choices");
                vote.handleClick(first.player, first.opened.getItem(16));
                check(vote.votesFor(ClassicVoteService.RANDOM) == 1, "Random vote recorded once");
                var finish = ClassicVoteService.class.getDeclaredMethod("finishVote"); finish.setAccessible(true); finish.invoke(vote);
                waiting(first);
                check(first.location.equals(map.arenaWaitingLobbyLocation), "voted join reaches waiting lobby");
                check(!first.location.equals(map.arenaRunnerSpawnLocations.getFirst()), "waiting differs from race spawn");
                var signs = new pl.mrstudios.deathrun.arena.sign.SignManager(plugin, manager, configuration);
                manager.setSignManager(signs);
                Actor queued=actor();
                check(signs.queuePlayerToMap(queued.player,map.id), "sign queues player");
                check(queued.location.equals(map.arenaWaitingLobbyLocation) && manager.runtimeForPlayer(queued.player)==null, "queue waits in lobby before promotion");
                check(manager.applyQueuedPlayersForMapStart(map.id)==1, "queue promotes once"); waiting(queued);
                check(manager.applyQueuedPlayersForMapStart(map.id)==0, "queue promotion idempotent");
                for (int i=2; i<10; i++) join(actor());
                round.run(); check(arena.getGameState()==WAITING, "10 players do not auto-start");
                Actor eleventh=actor(); join(eleventh); round.run();
                check(arena.getGameState()==STARTING, "11 players start countdown");
                check(manager.leaveCurrentMap(eleventh.player,true), "player leaves during countdown"); restored(eleventh);
                round.run(); check(arena.getGameState()==WAITING, "10 players cancel countdown");
                for (Actor actor: active()) waiting(actor);
                check(round.startingTimerForDisplay()==31, "cancel resets countdown");
                join(eleventh);
                while(arena.getUsers().size()<22) join(actor());
                check(manager.joinMap(first.player,map.id)==ArenaManager.JoinResult.ALREADY_IN_MAP, "repeat join full room is idempotent");
                Actor overflow=actor();
                check(manager.joinMap(overflow.player,map.id)==ArenaManager.JoinResult.MAP_FULL, "23rd player rejected"); restored(overflow);
                round.run(); for(int i=0;i<31;i++) round.run();
                check(arena.getGameState()==PLAYING, "countdown enters PLAYING");
                check(arena.getRunners().size()==20 && arena.getDeaths().size()==2, "20 Runner + 2 Death");
                Actor runner=forPlayer(arena.getRunners().getFirst().asBukkit());
                var user=arena.getUser(runner.player);
                check(user.getLives()==2 && user.getCheckpoint().id()==0, "2 lives and initial start checkpoint");
                check(map.arenaRunnerSpawnLocations.contains(runner.location), "Runner starts on race grid");
                var strafe=new ClassicStrafeService(plugin);
                check(strafe.directionOf(runner.items[3])==ClassicStrafeService.Direction.LEFT
                        && strafe.directionOf(runner.items[4])==ClassicStrafeService.Direction.BACK
                        && strafe.directionOf(runner.items[5])==ClassicStrafeService.Direction.RIGHT, "Strafe slots 4/5/6");
                Actor death=forPlayer(arena.getDeaths().getFirst().asBukkit());
                var nav=new DeathNavigatorService(plugin);
                check(nav.actionOf(death.items[0])==DeathNavigatorService.Action.PREVIOUS
                        && nav.actionOf(death.items[4])==DeathNavigatorService.Action.JUMP
                        && nav.actionOf(death.items[8])==DeathNavigatorService.Action.NEXT, "Death slots 1/5/9");
                for(int slot:new int[]{1,2,3,5,6,7}) check(nav.actionOf(death.items[slot])==DeathNavigatorService.Action.ACTIVATE,"Death activation slot "+slot);
                var handle=manager.runtimeForPlayer(death.player);
                check(nav.previous(death.player,handle)==22 && nav.next(death.player,handle)==0,"Death navigation wraps 23 traps");
                check(nav.jump(death.player,handle),"Death Jumper reaches safe control landing");
                check(deaths.killRunner(runner.player,DeathRunDeathCause.values()[0])==null,"no death before barrier release");
                for(int i=0;i<9;i++) round.run(); check(arena.getRemainingTime()==300,"barrier countdown preserves game time");
                round.run(); check(round.barrierTimerForDisplay()==0 && arena.getRemainingTime()==299,"barrier releases and timer starts");
                check(strafe.activate(runner.player,ClassicStrafeService.Direction.LEFT),"Left Strafe activates");
                check(!strafe.activate(runner.player,ClassicStrafeService.Direction.LEFT),"Left rejects cooldown spam");
                check(strafe.activate(runner.player,ClassicStrafeService.Direction.BACK) && strafe.activate(runner.player,ClassicStrafeService.Direction.RIGHT),"Back/Right independent cooldowns");
                check(Math.abs(runner.velocity.getY()-.30)<1e-9 && Math.abs(Math.hypot(runner.velocity.getX(),runner.velocity.getZ())-1.78)<1e-9,"Strafe velocity fixed");
                check(deaths.killRunner(runner.player,DeathRunDeathCause.values()[0]).remainingLives()==1,"first death costs one life");
                check(runner.location.equals(user.getCheckpoint().spawn()),"death before CP respawns at Runner start");
                check(deaths.killRunner(runner.player,DeathRunDeathCause.values()[0])==null,"duplicate hazard debounced");
                DeathRunDeathService.clearPlayer(runner.id); // advance debounce without waiting or changing gameplay time
                check(deaths.killRunner(runner.player,DeathRunDeathCause.values()[0]).eliminated(),"second death eliminates");
                check(user.getRole()==SPECTATOR && runner.allowFlight,"zero lives becomes spectator");
                check(strafe.remainingMillis(runner.player,ClassicStrafeService.Direction.LEFT)==0,"elimination clears cooldown");
                waitingBed(runner);
                Actor finisher=forPlayer(arena.getRunners().getFirst().asBukkit());
                var finishing=arena.getUser(finisher.player);
                touch(finisher,map.arenaCheckpoints.size()-1);
                check(finishing.getCheckpoint().id()==0 && arena.getFinishedRuns()==0,"finish shortcut rejected");
                int expectedPoints=0;
                for(int i=0;i<map.arenaCheckpoints.size();i++) {
                    int lives=finishing.getLives(); touch(finisher,i);
                    check(finishing.getCheckpoint().id().equals(map.arenaCheckpoints.get(i).id()),"ordered checkpoint "+i);
                    expectedPoints+=map.arenaCheckpointPoints.get(i);
                    if(i<map.arenaCheckpoints.size()-1) {
                        check(finishing.getLives()==lives+2,"normal checkpoint awards 2 lives");
                        int points=finishing.getRoundPoints(); touch(finisher,i);
                        check(finishing.getRoundPoints()==points && finishing.getLives()==lives+2,"checkpoint revisit gives no reward");
                    } else check(finishing.getLives()==lives,"finish gives no extra 2 lives");
                }
                expectedPoints+=finishing.getLives();
                check(arena.getFinishedRuns()==1 && finishing.getRoundPoints()==expectedPoints,"finish converts remaining lives into points");
                check(arena.getRemainingTime()==60 && finishing.getRole()==SPECTATOR,"first finish clamps remaining time to 60");
                waitingBed(finisher); touch(finisher,map.arenaCheckpoints.size()-1);
                check(arena.getFinishedRuns()==1,"duplicate finish rejected");
                Actor late=actor(); check(manager.joinMap(late.player,map.id)==ArenaManager.JoinResult.MATCH_IN_PROGRESS,"mid-game join blocked");
                for(int i=0;i<60;i++) round.run(); check(arena.getGameState()==ENDING,"timer expires into ENDING");
                for(Actor actor:active()) waitingBed(actor);
                for(int i=0;i<15;i++) round.run();
                check(arena.getGameState()==WAITING && arena.getUsers().isEmpty(),"settlement returns empty room to WAITING");
                for(Actor actor:actors.values()) restored(actor);
                // A fresh session must reset timer, lives, points, inventory and old snapshot ownership.
                join(first); check(manager.forceStartMap(map.id)==ArenaManager.ForceStartResult.STARTED,"force-start one-player debug session");
                for(int i=0;i<4;i++) round.run();
                check(arena.getGameState()==PLAYING && arena.getRunners().size()==1 && arena.getDeaths().isEmpty(),"single-player debug start");
                check(arena.getRemainingTime()==300 && arena.getUser(first.player).getLives()==2 && arena.getUser(first.player).getRoundPoints()==0,"next round has clean counters");
                first.online=false; // quit handler performs the same leave operation while Player is still available
                first.online=true; manager.leaveCurrentMap(first.player,false); first.online=false;
                check(!manager.hasPendingSnapshot(first.player),"disconnect restores and retires snapshot");
                first.online=true; restored(first); round.run(); for(int i=0;i<15;i++) round.run();
                check(arena.getGameState()==WAITING,"last Runner quitting ends round");
                // Rejected cancellation teleport affects only that player, and restores the original snapshot.
                for(int i=0;i<11;i++) join(new ArrayList<>(actors.values()).get(i)); round.run();
                Actor leaving=active().getLast(); manager.leaveCurrentMap(leaving.player,false);
                Actor rejected=active().getFirst(); rejected.rejectTarget=map.arenaWaitingLobbyLocation.clone();
                round.run();
                check(arena.getGameState()==WAITING && manager.runtimeForPlayer(rejected.player)==null,"rejected return-to-wait teleport detaches player");
                restored(rejected); rejected.rejectTarget=null;
                for(Actor actor:new ArrayList<>(active())) manager.leaveCurrentMap(actor.player,false);
                Actor rejectedJoin=actor(); rejectedJoin.rejectTarget=map.arenaWaitingLobbyLocation.clone();
                check(manager.joinMap(rejectedJoin.player,map.id)==ArenaManager.JoinResult.PLAYER_TELEPORT_FAILED,"rejected initial waiting teleport reported");
                restored(rejectedJoin);
                owner.getLogger().info("[DR-FLOW] PASS assertions="+checks+" scenarios=vote,waiting,countdown-cancel,22-player-start,strafe,death,checkpoints,finish,timeout,settlement,next-round,disconnect,teleport-rejection");
            } finally {
                for(Actor actor:actors.values()) {
                    actor.online=true; actor.rejectTarget=null;
                    manager.leaveCurrentMap(actor.player,false);
                    ClassicStrafeService.clearCooldowns(actor.id); DeathRunDeathService.clearPlayer(actor.id); DeathNavigatorService.clearPlayer(actor.id);
                }
                barriers.forEach((location,state)->location.getBlock().setBlockData(state));
                global.set(null,real);
                try(var paths=Files.walk(data)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
            }
        }
        Actor actor() { Actor actor=new Actor("Flow"+actors.size(),real.getWorlds().getFirst().getSpawnLocation()); actors.put(actor.id,actor); return actor; }
        Actor forPlayer(Player player) { return actors.get(player.getUniqueId()); }
        List<Actor> active() { return arena.getUsers().stream().map(u->actors.get(u.getUniqueId())).toList(); }
        void join(Actor actor) { check(manager.joinMap(actor.player,map.id)==ArenaManager.JoinResult.JOINED,"join "+actor.name); waiting(actor); }
        void waitingBed(Actor actor) { check(actor.items[8]!=null && actor.items[8].getType()==Material.RED_BED,"exit bed "+actor.name); }
        void waiting(Actor actor) { check(actor.location.equals(map.arenaWaitingLobbyLocation),"waiting location "+actor.name); waitingBed(actor); }
        void restored(Actor actor) {
            check(actor.location.equals(actor.original),"original location restored "+actor.name);
            check(actor.mode==GameMode.SURVIVAL && actor.food==13,"original mode/hunger restored "+actor.name);
            check(actor.items[0]!=null && actor.items[0].equals(new ItemStack(Material.DIAMOND,3)),"original inventory restored "+actor.name);
            check(!manager.hasPendingSnapshot(actor.player),"journal retired "+actor.name);
        }
        void touch(Actor actor,int index) {
            Location location=map.arenaCheckpoints.get(index).locations().getFirst().clone().add(.5,0,.5);
            actor.location=location.clone();
            checkpoints.onPlayerTeleport(new PlayerTeleportEvent(actor.player, location, location));
        }
        void check(boolean value,String label) { if(!value) throw new AssertionError("step "+checks+": "+label); checks++; }
    }

    static final class Actor {
        final UUID id=UUID.randomUUID(); final String name;
        final Location original; Location location, rejectTarget;
        ItemStack[] items=new ItemStack[36], armor=new ItemStack[4]; ItemStack offhand;
        final Player player; boolean online=true, allowFlight, flying;
        GameMode mode=GameMode.SURVIVAL; int food=13, held; float saturation=3;
        Vector velocity=new Vector(); Inventory opened;
        final Map<org.bukkit.potion.PotionEffectType,PotionEffect> effects=new HashMap<>();
        Actor(String name,Location original) {
            this.name=name; this.original=original.clone(); this.location=original.clone(); items[0]=new ItemStack(Material.DIAMOND,3);
            PlayerInventory inventory=proxy(PlayerInventory.class,(method,args)->switch(method.getName()) {
                case "getStorageContents" -> items.clone(); case "getArmorContents" -> armor.clone();
                case "getItemInOffHand" -> offhand; case "getHeldItemSlot" -> held;
                case "getItem" -> items[(int)args[0]];
                case "clear" -> { if(args==null || args.length==0) Arrays.fill(items,null); else items[(int)args[0]]=null; yield null; }
                case "setItem" -> { items[(int)args[0]]=(ItemStack)args[1]; yield null; }
                case "setStorageContents" -> { items=((ItemStack[])args[0]).clone(); yield null; }
                case "setArmorContents" -> { armor=((ItemStack[])args[0]).clone(); yield null; }
                case "setItemInOffHand" -> { offhand=(ItemStack)args[0]; yield null; }
                case "setHeldItemSlot" -> { held=(int)args[0]; yield null; }
                default -> defaultValue(method.getReturnType());
            });
            Scoreboard scoreboard=proxy(Scoreboard.class,(method,args)->switch(method.getName()) {
                case "getObjectives", "getTeams", "getEntries" -> Set.of(); default -> defaultValue(method.getReturnType());
            });
            player=proxy(Player.class,(method,args)->switch(method.getName()) {
                case "getDisplayName" -> name; case "getUniqueId" -> id; case "getName" -> name; case "getServer" -> Bukkit.getServer();
                case "getWorld" -> location.getWorld(); case "getLocation" -> location.clone();
                case "getInventory" -> inventory; case "getScoreboard" -> scoreboard;
                case "getGameMode" -> mode; case "setGameMode" -> { mode=(GameMode)args[0]; yield null; }
                case "getHealth", "getMaxHealth" -> 20.0;
                case "getFoodLevel" -> food; case "setFoodLevel" -> { food=(int)args[0]; yield null; }
                case "getSaturation" -> saturation; case "setSaturation" -> { saturation=(float)args[0]; yield null; }
                case "isOnline" -> online; case "getAllowFlight" -> allowFlight; case "isFlying" -> flying;
                case "setAllowFlight" -> { allowFlight=(boolean)args[0]; yield null; }
                case "setFlying" -> { flying=(boolean)args[0]; yield null; }
                case "getActivePotionEffects" -> new ArrayList<>(effects.values());
                case "addPotionEffect" -> { PotionEffect effect=(PotionEffect)args[0]; effects.put(effect.getType(),effect); yield true; }
                case "removePotionEffect" -> { effects.remove(args[0]); yield null; }
                case "teleport" -> {
                    Location target=(Location)args[0]; if(target.equals(rejectTarget)) yield false;
                    location=target.clone(); yield true;
                }
                case "setVelocity" -> { velocity=((Vector)args[0]).clone(); yield null; }
                case "getVelocity" -> velocity.clone();
                case "openInventory" -> { opened=(Inventory)args[0]; yield null; }
                default -> defaultValue(method.getReturnType());
            });
        }
    }
    static Object field(Object target,String name) throws Exception { Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    static Object invoke(Object target,Method method,Object[] args) {
        try { return method.invoke(target,args); } catch(ReflectiveOperationException failure) { throw new RuntimeException(failure); }
    }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,BiFunction<Method,Object[],Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(self,method,args)-> {
            if(method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                case "equals" -> self==args[0]; case "hashCode" -> System.identityHashCode(self); default -> type.getSimpleName()+"Proxy";
            };
            return handler.apply(method,args);
        });
    }
    static Object defaultValue(Class<?> type) {
        if(!type.isPrimitive() || type==void.class) return null;
        if(type==boolean.class) return false; if(type==float.class) return 0f; if(type==double.class) return 0d;
        if(type==long.class) return 0L; if(type==short.class) return (short)0; if(type==byte.class) return (byte)0;
        if(type==char.class) return '\0'; return 0;
    }
}
