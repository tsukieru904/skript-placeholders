package io.github.apickledwalrus.skriptplaceholders.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Folia/Paper scheduler helpers.
 *
 * <p>Important: synchronous PlaceholderAPI callbacks cannot safely block a Folia region while
 * waiting for another region. Therefore all cross-region work is scheduled asynchronously and
 * the caller must use a cached/previous value when an immediate value cannot be produced.</p>
 */
public final class FoliaScheduler {

    private FoliaScheduler() {
    }

    public static boolean isGlobalThread() {
        return Bukkit.isPrimaryThread() || Bukkit.isGlobalTickThread();
    }

    public static boolean owns(Player player) {
        return Bukkit.isOwnedByCurrentRegion(player);
    }

    /**
     * Runs immediately when already on the global/main thread; otherwise schedules it without
     * blocking the current region thread.
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (isGlobalThread()) {
            task.run();
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    /**
     * Runs immediately when this thread owns the player. Otherwise schedules the work on the
     * player's entity scheduler. No blocking wait is ever performed.
     *
     * @return true when the task ran immediately, false when it was queued.
     */
    public static boolean runForPlayer(Plugin plugin, @Nullable OfflinePlayer offlinePlayer, Runnable task) {
        Player player = offlinePlayer != null ? offlinePlayer.getPlayer() : null;
        if (player == null) {
            if (isGlobalThread()) {
                task.run();
                return true;
            }
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
            return false;
        }

        if (owns(player)) {
            task.run();
            return true;
        }

        player.getScheduler().run(plugin, scheduledTask -> task.run(), () -> { });
        return false;
    }

    /**
     * Runs relational work only when the current region owns both entities. A cross-region
     * relational operation is not executed because Folia has no safe scheduler that owns two
     * independent entities at once.
     *
     * @return true when the task ran immediately, false when it could not be executed now.
     */
    public static boolean runRelational(Plugin plugin, Player first, Player second, Runnable task) {
        if (owns(first) && owns(second)) {
            task.run();
            return true;
        }
        return false;
    }
}
