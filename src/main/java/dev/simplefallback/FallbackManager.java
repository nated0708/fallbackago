package dev.simplefallback;

import net.md_5.bungee.api.Title;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Decides where a player should go and performs the move. */
public class FallbackManager {

    private final FallbackPlugin plugin;

    public FallbackManager(FallbackPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves the first usable fallback for a player.
     *
     * @param from the server the player is being removed from (may be null)
     * @return an online fallback server, or null if none is available
     */
    public ServerInfo resolve(ProxiedPlayer player, ServerInfo from) {
        String fromName = from == null ? null : from.getName();

        Set<String> candidates = new LinkedHashSet<>();
        if (fromName != null) {
            candidates.addAll(plugin.config().getFallbacksFor(fromName));
        }
        candidates.addAll(plugin.config().getGlobalFallback());

        for (String name : candidates) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (fromName != null && name.equalsIgnoreCase(fromName)) {
                continue; // never bounce back into the dead server
            }
            ServerInfo target = getServer(name);
            if (target == null) {
                plugin.getLogger().warning("Fallback server '" + name + "' is not defined in the proxy config.");
                continue;
            }
            if (!plugin.watcher().isAlive(target.getName())) {
                continue;
            }
            if (plugin.config().isPerServerPermission()
                    && !player.hasPermission("simplefallback.server." + target.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            return target;
        }
        return null;
    }

    /** Case-insensitive server lookup. */
    public ServerInfo getServer(String name) {
        ServerInfo direct = plugin.getProxy().getServerInfo(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, ServerInfo> e : plugin.getProxy().getServers().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Connects the player to a fallback. Used for evacuations (the player is
     * still online but their server is dead). If nothing is available the
     * player is disconnected with the configured message.
     */
    public void moveToFallback(ProxiedPlayer player, ServerInfo from, String reason) {
        ServerInfo target = resolve(player, from);
        String fromName = from == null ? "unknown" : from.getName();

        if (target == null) {
            String msg = plugin.lang().get("no-fallback-available",
                    Lang.placeholders("player", player.getName(), "from", fromName, "reason", reason));
            if (msg.isEmpty()) {
                msg = "No fallback server available.";
            }
            player.disconnect(TextComponent.fromLegacyText(msg));
            return;
        }

        player.connect(target, (success, error) -> {
            if (success != null && success) {
                notifyPlayer(player, fromName, target.getName(), reason);
            } else {
                // Target refused us, try again minus that server.
                plugin.getLogger().warning("Failed to move " + player.getName()
                        + " to '" + target.getName() + "', flagging it and retrying.");
                plugin.watcher().markDead(target.getName());
                moveToFallback(player, from, reason);
            }
        }, ServerConnectEvent.Reason.PLUGIN);
    }

    /** Sends the chat message + title, slightly delayed so it survives the switch. */
    public void notifyPlayer(ProxiedPlayer player, String fromName, String toName, String reason) {
        Map<String, String> ph = Lang.placeholders(
                "player", player.getName(),
                "from", fromName == null ? "unknown" : fromName,
                "to", toName,
                "reason", reason == null ? "" : reason);

        Runnable send = () -> {
            if (!player.isConnected()) {
                return;
            }
            plugin.lang().send(player, "fallback-success", ph);
            Title title = plugin.lang().buildTitle(ph);
            if (title != null) {
                title.send(player);
            }
        };

        long delay = plugin.config().getMessageDelay();
        if (delay <= 0) {
            send.run();
        } else {
            plugin.getProxy().getScheduler().schedule(plugin, send, delay, TimeUnit.MILLISECONDS);
        }
    }
}
