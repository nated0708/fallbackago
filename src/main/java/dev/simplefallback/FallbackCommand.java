package dev.simplefallback;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Command;

import java.util.ArrayList;
import java.util.List;

public class FallbackCommand extends Command {

    private final FallbackPlugin plugin;

    public FallbackCommand(FallbackPlugin plugin) {
        super("sfallback", null, "simplefallback", "fallbackreload");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefallback.admin")) {
            plugin.lang().send(sender, "no-permission", null);
            return;
        }

        if (args.length == 0) {
            plugin.lang().send(sender, "usage", null);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                plugin.lang().send(sender, "reloaded", null);
            }
            case "status" -> {
                plugin.lang().send(sender, "status-header", null);
                List<ServerInfo> servers = new ArrayList<>(plugin.getProxy().getServers().values());
                servers.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (ServerInfo info : servers) {
                    boolean up = plugin.watcher().isAlive(info.getName());
                    plugin.lang().send(sender,
                            up ? "status-line-online" : "status-line-offline",
                            Lang.placeholders("server", info.getName()));
                }
            }
            default -> plugin.lang().send(sender, "usage", null);
        }
    }
}
