package pl.mrstudios.deathrun.classic.tobee;

import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import net.kyori.adventure.text.Component;
import java.util.List;

public final class ToBeeSignTranslations {
    private ToBeeSignTranslations() {}
    public static final List<Entry> ENTRIES = List.of(
            new Entry(-62, 25, 24, List.of("警告！", "冰面随时可能融化", "", "")),
            new Entry(-57, 25, -14, List.of("注意！", "深色木块不稳定", "可能突然消失", "")),
            new Entry(-55, 25, 0, List.of("当心！", "前方可能出现墙壁", "", "")),
            new Entry(-46, 25, 20, List.of("警告！", "地板可能被淹没", "", "")),
            new Entry(-45, 29, -18, List.of("移除深色木块", "深色木块不稳定", "可能突然消失", "<<<")),
            new Entry(-45, 29, 2, List.of("召唤随机墙壁", "前方可能出现墙壁", ">>>", "")),
            new Entry(-45, 29, 7, List.of("冰块融化", "融化冰块", "<<<", "")),
            new Entry(-41, 18, -34, List.of("当心！", "火焰箭会穿过赛道", "", "")),
            new Entry(-41, 29, 10, List.of("淹没地板", "地板可能被淹没", "<<<", "")),
            new Entry(-35, 29, -19, List.of("发射火焰箭", "火焰箭会穿过赛道", "<<<", "")),
            new Entry(-25, 18, -34, List.of("警告！", "红色方块可能消失", "", "")),
            new Entry(-23, 29, 10, List.of("发射火焰箭", "火焰箭会穿过赛道", ">>>", "")),
            new Entry(-21, 29, -19, List.of("移除红色方块", "红色方块可能消失", ">>>", "")),
            new Entry(-19, 25, 20, List.of("当心！", "火焰箭会穿过赛道", "", "")),
            new Entry(-5, 29, 10, List.of("召唤随机墙壁", "前方可能出现墙壁", ">>>", "")),
            new Entry(-4, 25, 31, List.of("当心！", "前方可能出现墙壁", "", "")),
            new Entry(6, 25, 46, List.of("当心！", "前方是致命雷区", "", "")),
            new Entry(6, 25, 60, List.of("当心！", "火蛇会沿赛道袭来", "", "")),
            new Entry(6, 25, 72, List.of("当心！", "火焰箭会穿过赛道", "", "")),
            new Entry(7, 25, -36, List.of("注意！", "只有绿色方块", "可以安全落脚", "")),
            new Entry(10, 27, 67, List.of("发射火焰箭", "火焰箭会穿过赛道", "<<<", "")),
            new Entry(11, 29, 40, List.of("引爆雷区", "雷区机关", ">>> ", "")),
            new Entry(11, 29, 55, List.of("释放火蛇", "火蛇会沿赛道袭来", ">>>", "")),
            new Entry(13, 29, -19, List.of("移除红色方块", "红色方块可能消失", "<<<", "")),
            new Entry(16, 25, 76, List.of("移除深色木块", "深色木块不稳定", "可能突然消失", "<<<")),
            new Entry(22, 25, -33, List.of("当心！", "前方可能出现墙壁", "", "")),
            new Entry(23, 29, -19, List.of("召唤随机墙壁", "前方可能出现墙壁", "<<<", "")),
            new Entry(24, 35, 79, List.of("注意！", "深色木块不稳定", "可能突然消失", "")),
            new Entry(26, 35, 51, List.of("蜂与不蜂", "", "作者：", "Timmetatsch")),
            new Entry(27, 44, 21, List.of("淹没地板", "地板可能被淹没", "<<<", "")),
            new Entry(28, 29, -17, List.of("移除地板", "地板可能突然消失", "<<<", "")),
            new Entry(29, 44, 9, List.of("落下炸药", "<<<", "", "")),
            new Entry(30, 44, 15, List.of("点燃煤块", "煤块可能燃烧", ">>>", "")),
            new Entry(32, 25, 76, List.of("移除红色方块", "红色方块可能消失", ">>>", "")),
            new Entry(32, 45, 11, List.of("警告！", "煤块可能燃烧", "", "")),
            new Entry(33, 25, -28, List.of("当心！", "地板可能突然消失", "", "")),
            new Entry(36, 45, 21, List.of("警告！", "地板可能被淹没", "", "")),
            new Entry(37, 34, 85, List.of("警告！", "红色方块可能消失", "", "")),
            new Entry(50, 25, 53, List.of("当心！", "地板可能突然消失", "", "")),
            new Entry(52, 25, 56, List.of("移除地板", "地板可能突然消失", ">>>", "")),
            new Entry(70, 25, 53, List.of("当心！", "火蛇会沿赛道袭来", "", "")),
            new Entry(72, 25, 76, List.of("移除海晶灯", ">>>", "", "")),
            new Entry(76, 25, 56, List.of("释放火蛇", "火蛇会沿赛道袭来", ">>>", "")),
            new Entry(77, 25, 80, List.of("提示：", "往下看！", "↓", "↓")),
            new Entry(89, 25, 65, List.of("移除红色方块", "红色方块可能消失", ">>>", "")),
            new Entry(93, 25, 56, List.of("警告！", "红色方块可能消失", "", ""))
    );
    public static int apply(World world) {
        int changed = 0;
        for (var entry : ENTRIES) {
            if (!(world.getBlockAt(entry.x(), entry.y(), entry.z()).getState() instanceof Sign sign))
                continue;
            for (int line = 0; line < 4; line++)
                sign.getSide(Side.FRONT).line(line, Component.text(entry.lines().get(line)));
            if (!sign.update(true, false))
                throw new IllegalStateException("Sign update failed: " + sign.getLocation());
            changed++;
        }
        return changed;
    }
    public record Entry(int x, int y, int z, List<String> lines) {}
}
