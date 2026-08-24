package dev.simplefallback;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically pings every backend server so we always know which ones are up.
 * This is what makes the fallback reliable: we never send a player to a server
 * that is itself dead, and we can pull players off a server that froze.
 */
public class ServerWatcher {

    private final FallbackPlugin plugin;

    private final Map<String, Boolean> alive = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> successes = new ConcurrentHashMap<>();

    private ScheduledTask task;

    public ServerWatcher(FallbackPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.config().isHealthCheckEnabled()) {
            return;
        }
        int interval = plugin.config().getInterval();
        this.task = plugin.getProxy().getScheduler().schedule(
                plugin, this::pingAll, 1L, interval, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        alive.clear();
        failures.clear();
        successes.clear();
    }

    private void pingAll() {
        for (ServerInfo info : new ArrayList<>(plugin.getProxy().getServers().values())) {
            pingServer(info);
        }
    }

    private void pingServer(ServerInfo info) {
        final String key = key(info.getName());
        Callback<ServerPing> callback = (result, error) -> {
            if (error != null || result == null) {
                onFailure(info);
            } else {
                onSuccess(info);
            }
        };
        try {
            info.ping(callback);
        } catch (Exception ex) {
            onFailure(info);
        }
        // keep the key referenced so maps are initialised for /sfallback status
        alive.putIfAbsent(key, Boolean.TRUE);
    }

    private void onSuccess(ServerInfo info) {
        String key = key(info.getName());
        failures.computeIfAbsent(key, k -> new AtomicInteger()).set(0);
        int ok = successes.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();

        if (!isAlive(info.getName()) && ok >= plugin.config().getSuccessesBeforeAlive()) {
            alive.put(key, Boolean.TRUE);
            plugin.getLogger().info("Server '" + info.getName() + "' is back online.");
        }
    }

    private void onFailure(ServerInfo info) {
        String key = key(info.getName());
        successes.computeIfAbsent(key, k -> new AtomicInteger()).set(0);
        int fails = failures.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();

        if (isAlive(info.getName()) && fails >= plugin.config().getFailuresBeforeDead()) {
            alive.put(key, Boolean.FALSE);
            plugin.getLogger().warning("Server '" + info.getName() + "' appears to be DOWN.");
            if (plugin.config().isEvacuateOnDeath()) {
                evacuate(info);
            }
        }
    }

    /** Pull every player still connected to a dead server to their fallback. */
    private void evacuate(ServerInfo dead) {
        List<ProxiedPlayer> players = new ArrayList<>(dead.getPlayers());
        if (players.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Evacuating " + players.size() + " player(s) from '" + dead.getName() + "'.");
        for (ProxiedPlayer player : players) {
            plugin.fallbacks().moveToFallback(player, dead, "Server went down");
        }
    }

    /** Immediately flag a server dead (used when a kick message says it crashed). */
    public void markDead(String serverName) {
        if (serverName == null) {
            return;
        }
        String key = key(serverName);
        alive.put(key, Boolean.FALSE);
        successes.computeIfAbsent(key, k -> new AtomicInteger()).set(0);
        failures.computeIfAbsent(key, k -> new AtomicInteger())
                .set(plugin.config().getFailuresBeforeDead());
    }

    /** Flag a server alive immediately (a real connection beats a ping poll). */
    public void markAlive(String serverName) {
        if (serverName == null) {
            return;
        }
        String key = key(serverName);
        alive.put(key, Boolean.TRUE);
        failures.computeIfAbsent(key, k -> new AtomicInteger()).set(0);
        successes.computeIfAbsent(key, k -> new AtomicInteger())
                .set(plugin.config().getSuccessesBeforeAlive());
    }

    /** Unknown or unchecked servers are assumed alive. */
    public boolean isAlive(String serverName) {
        if (serverName == null) {
            return false;
        }
        if (!plugin.config().isHealthCheckEnabled()) {
            return true;
        }
        return alive.getOrDefault(key(serverName), Boolean.TRUE);
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
