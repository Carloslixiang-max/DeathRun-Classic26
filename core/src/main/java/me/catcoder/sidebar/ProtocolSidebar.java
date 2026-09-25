package me.catcoder.sidebar;

import org.bukkit.plugin.Plugin;

public final class ProtocolSidebar {

    private ProtocolSidebar() {}

    public static <T> Sidebar<T> newAdventureSidebar(T title, Plugin plugin) {
        return new Sidebar<>(title, plugin);
    }
}
