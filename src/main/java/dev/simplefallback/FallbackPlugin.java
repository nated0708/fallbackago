package dev.simplefallback;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FallbackPlugin extends Plugin {

    private static FallbackPlugin instance;

    private FallbackConfig config;
    private Lang lang;
    private ServerWatcher watcher;
    private FallbackManager fallbackManager;

    public static FallbackPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        loadFiles();

        this.fallbackManager = new FallbackManager(this);
        this.watcher = new ServerWatcher(this);
        this.watcher.start();

        getProxy().getPluginManager().registerListener(this, new FallbackListener(this));
        getProxy().getPluginManager().registerCommand(this, new FallbackCommand(this));

        getLogger().info("SimpleFallback enabled. Health check: "
                + (config.isHealthCheckEnabled() ? "on (" + config.getInterval() + "s)" : "off"));
    }

    @Override
    public void onDisable() {
        if (watcher != null) {
            watcher.stop();
        }
        getProxy().getScheduler().cancel(this);
    }

    /** Loads / reloads config.yml and lang.yml. */
    public void loadFiles() {
        Configuration rawConfig = loadYaml("config.yml");
        Configuration rawLang = loadYaml("lang.yml");

        this.config = new FallbackConfig(rawConfig);
        this.lang = new Lang(rawLang);
    }

    public void reload() {
        loadFiles();
        if (watcher != null) {
            watcher.stop();
            watcher.start();
        }
    }

    private Configuration loadYaml(String name) {
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                getLogger().warning("Could not create data folder!");
            }
            File file = new File(getDataFolder(), name);
            if (!file.exists()) {
                try (InputStream in = getResourceAsStream(name)) {
                    if (in == null) {
                        throw new IOException("Bundled resource " + name + " is missing from the jar");
                    }
                    Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            getLogger().severe("Failed to load " + name + ": " + e.getMessage());
            // Empty configuration so the plugin still runs with defaults.
            return new Configuration();
        }
    }

    public FallbackConfig config() {
        return config;
    }

    public Lang lang() {
        return lang;
    }

    public ServerWatcher watcher() {
        return watcher;
    }

    public FallbackManager fallbacks() {
        return fallbackManager;
    }
}
