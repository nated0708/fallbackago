package dev.simplefallback;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.Title;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;

import java.util.HashMap;
import java.util.Map;

/** Loads lang.yml and formats messages / titles. */
public class Lang {

    private final Configuration cfg;
    private final String prefix;

    public Lang(Configuration cfg) {
        this.cfg = cfg;
        this.prefix = color(cfg.getString("prefix", ""));
    }

    public static String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    /** Returns a coloured, placeholder-filled message, or "" if disabled. */
    public String get(String key, Map<String, String> placeholders) {
        String raw = cfg.getString(key, "");
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        raw = raw.replace("%prefix%", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
        }
        return color(raw);
    }

    public String get(String key) {
        return get(key, null);
    }

    public void send(ProxiedPlayer player, String key, Map<String, String> placeholders) {
        String msg = get(key, placeholders);
        if (!msg.isEmpty()) {
            player.sendMessage(TextComponent.fromLegacyText(msg));
        }
    }

    public void send(net.md_5.bungee.api.CommandSender sender, String key, Map<String, String> placeholders) {
        String msg = get(key, placeholders);
        if (!msg.isEmpty()) {
            sender.sendMessage(TextComponent.fromLegacyText(msg));
        }
    }

    public boolean isTitleEnabled() {
        return cfg.getBoolean("title.enabled", false);
    }

    /** Builds the configured title, or null if it is disabled / empty. */
    public Title buildTitle(Map<String, String> placeholders) {
        if (!isTitleEnabled()) {
            return null;
        }
        String title = get("title.title", placeholders);
        String subtitle = get("title.subtitle", placeholders);
        if (title.isEmpty() && subtitle.isEmpty()) {
            return null;
        }
        return ProxyServer.getInstance().createTitle()
                .title(TextComponent.fromLegacyText(title))
                .subTitle(TextComponent.fromLegacyText(subtitle))
                .fadeIn(cfg.getInt("title.fade-in", 10))
                .stay(cfg.getInt("title.stay", 60))
                .fadeOut(cfg.getInt("title.fade-out", 10));
    }

    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
