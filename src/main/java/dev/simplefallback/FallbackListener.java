package dev.simplefallback;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class FallbackListener implements Listener {

    private final FallbackPlugin plugin;

    public FallbackListener(FallbackPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Main path: the backend kicked the player, or the proxy lost the backend
     * and is about to drop the player. We cancel the kick and reroute instead.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(ServerKickEvent event) {
        if (event.isCancelled()) {
            return;
        }

        ProxiedPlayer player = event.getPlayer();
        ServerInfo from = event.getKickedFrom();
        String reason = plain(event.getKickReasonComponent());

        // Real kicks (bans, whitelist, ...) must go through untouched.
        if (plugin.config().isIgnoredKickReason(reason)) {
            return;
        }

        boolean crashKick = plugin.config().isServerDownReason(reason);
        if (crashKick && from != null) {
            // Flag it now so we never route anyone back into it.
            plugin.watcher().markDead(from.getName());
        }

        if (plugin.config().isRequireServerOffline() && !crashKick
                && from != null && plugin.watcher().isAlive(from.getName())) {
            return;
        }

        ServerInfo target = plugin.fallbacks().resolve(player, from);
        String fromName = from == null ? "unknown" : from.getName();

        if (target == null) {
            // Nothing alive to send them to - show our own disconnect message.
            String msg = plugin.lang().get("no-fallback-available",
                    Lang.placeholders("player", player.getName(), "from", fromName, "reason", reason));
            if (!msg.isEmpty()) {
                event.setKickReasonComponent(TextComponent.fromLegacyText(msg));
            }
            return;
        }

        event.setCancelled(true);
        event.setCancelServer(target);

        plugin.fallbacks().notifyPlayer(player, fromName, target.getName(), reason);
    }

    /**
     * Safety net: catches BungeeCord's own "server went down" redirects and any
     * connect attempt that is aimed at a server we know is dead (including the
     * very first join, when the default lobby is offline).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onConnect(ServerConnectEvent event) {
        if (event.isCancelled()) {
            return;
        }

        ProxiedPlayer player = event.getPlayer();
        ServerInfo target = event.getTarget();
        if (target == null) {
            return;
        }

        ServerInfo current = player.getServer() == null ? null : player.getServer().getInfo();
        ServerConnectEvent.Reason reason = event.getReason();

        // Another plugin (e.g. ajQueue) or a command chose this destination on
        // purpose. Never override it - our alive flag may simply be stale, and
        // redirecting here would steal players ajQueue just released from a queue.
        if (reason == ServerConnectEvent.Reason.PLUGIN
                || reason == ServerConnectEvent.Reason.COMMAND
                || reason == ServerConnectEvent.Reason.PLUGIN_MESSAGE) {
            return;
        }

        boolean downRedirect = reason == ServerConnectEvent.Reason.SERVER_DOWN_REDIRECT
                || reason == ServerConnectEvent.Reason.KICK_REDIRECT
                || reason == ServerConnectEvent.Reason.LOBBY_FALLBACK;

        boolean targetDead = !plugin.watcher().isAlive(target.getName());

        if (!downRedirect && !targetDead) {
            return; // normal /server, hub command, etc. - leave it alone
        }

        // Never let BungeeCord push a player back into the server they just fell out of.
        ServerInfo from = downRedirect && current != null ? current : (targetDead ? current : null);
        if (downRedirect && current != null && target.getName().equalsIgnoreCase(current.getName())) {
            targetDead = true;
        }

        if (!targetDead && downRedirect && isConfigured(from, target)) {
            return; // proxy already chose a server that matches our config and is up
        }

        ServerInfo replacement = plugin.fallbacks().resolve(player, from);
        if (replacement != null && !replacement.getName().equalsIgnoreCase(target.getName())) {
            event.setTarget(replacement);
        } else if (replacement == null && targetDead) {
            event.setCancelled(true);
            String msg = plugin.lang().get("no-fallback-available",
                    Lang.placeholders("player", player.getName(),
                            "from", from == null ? target.getName() : from.getName(),
                            "reason", "Server offline"));
            if (msg.isEmpty()) {
                msg = "No fallback server available.";
            }
            player.disconnect(TextComponent.fromLegacyText(msg));
        }
    }

    /**
     * A successful switch proves the server is up - clear any stale dead flag
     * immediately rather than waiting for the next health-check tick.
     */
    @EventHandler
    public void onSwitch(ServerSwitchEvent event) {
        if (event.getPlayer().getServer() != null) {
            plugin.watcher().markAlive(event.getPlayer().getServer().getInfo().getName());
        }
    }

    private boolean isConfigured(ServerInfo from, ServerInfo target) {
        String fromName = from == null ? null : from.getName();
        for (String name : plugin.config().getFallbacksFor(fromName)) {
            if (name.equalsIgnoreCase(target.getName())) {
                return true;
            }
        }
        for (String name : plugin.config().getGlobalFallback()) {
            if (name.equalsIgnoreCase(target.getName())) {
                return true;
            }
        }
        return false;
    }

    private String plain(BaseComponent[] components) {
        if (components == null) {
            return "";
        }
        return ChatColor.stripColor(BaseComponent.toLegacyText(components));
    }
}
