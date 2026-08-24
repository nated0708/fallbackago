package dev.simplefallback;

import net.md_5.bungee.config.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed view over config.yml. */
public class FallbackConfig {

    private final boolean healthCheckEnabled;
    private final int interval;
    private final int failuresBeforeDead;
    private final int successesBeforeAlive;
    private final boolean evacuateOnDeath;
    private final boolean requireServerOffline;
    private final boolean perServerPermission;
    private final long messageDelay;

    private final Map<String, List<String>> fallbacks = new HashMap<>();
    private final List<String> globalFallback;
    private final List<String> serverDownReasons = new ArrayList<>();
    private final List<String> ignoredKickReasons = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public FallbackConfig(Configuration cfg) {
        this.healthCheckEnabled = cfg.getBoolean("settings.health-check.enabled", true);
        this.interval = Math.max(1, cfg.getInt("settings.health-check.interval", 5));
        this.failuresBeforeDead = Math.max(1, cfg.getInt("settings.health-check.failures-before-dead", 2));
        this.successesBeforeAlive = Math.max(1, cfg.getInt("settings.health-check.successes-before-alive", 1));
        this.evacuateOnDeath = cfg.getBoolean("settings.evacuate-on-death", true);
        this.requireServerOffline = cfg.getBoolean("settings.require-server-offline", false);
        this.perServerPermission = cfg.getBoolean("settings.per-server-permission", false);
        this.messageDelay = Math.max(0, cfg.getLong("settings.message-delay", 500L));

        Configuration section = cfg.getSection("fallbacks");
        if (section != null) {
            for (String key : section.getKeys()) {
                Object value = section.get(key);
                List<String> targets = new ArrayList<>();
                if (value instanceof List) {
                    for (Object o : (List<Object>) value) {
                        if (o != null) {
                            targets.add(String.valueOf(o));
                        }
                    }
                } else if (value != null) {
                    targets.add(String.valueOf(value));
                }
                if (!targets.isEmpty()) {
                    fallbacks.put(key.toLowerCase(Locale.ROOT), targets);
                }
            }
        }

        this.globalFallback = new ArrayList<>(cfg.getStringList("global-fallback"));

        for (String s : cfg.getStringList("server-down-reasons")) {
            serverDownReasons.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : cfg.getStringList("ignored-kick-reasons")) {
            ignoredKickReasons.add(s.toLowerCase(Locale.ROOT));
        }
    }

    public List<String> getFallbacksFor(String serverName) {
        if (serverName == null) {
            return Collections.emptyList();
        }
        return fallbacks.getOrDefault(serverName.toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    public List<String> getGlobalFallback() {
        return globalFallback;
    }

    public boolean isServerDownReason(String plainKickMessage) {
        String msg = plainKickMessage == null ? "" : plainKickMessage.toLowerCase(Locale.ROOT);
        for (String needle : serverDownReasons) {
            if (!needle.isEmpty() && msg.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public boolean isIgnoredKickReason(String plainKickMessage) {
        String msg = plainKickMessage == null ? "" : plainKickMessage.toLowerCase(Locale.ROOT);
        for (String needle : ignoredKickReasons) {
            if (!needle.isEmpty() && msg.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public int getInterval() {
        return interval;
    }

    public int getFailuresBeforeDead() {
        return failuresBeforeDead;
    }

    public int getSuccessesBeforeAlive() {
        return successesBeforeAlive;
    }

    public boolean isEvacuateOnDeath() {
        return evacuateOnDeath;
    }

    public boolean isRequireServerOffline() {
        return requireServerOffline;
    }

    public boolean isPerServerPermission() {
        return perServerPermission;
    }

    public long getMessageDelay() {
        return messageDelay;
    }
}
