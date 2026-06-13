package net.summersmp.core;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /maces view|set|setlimit|reload */
public class MaceCommand implements CommandExecutor, TabCompleter {

    private final SummerSMPCore plugin;

    public MaceCommand(SummerSMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("summersmp.maces")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this.");
            return true;
        }
        String sub = (args.length == 0) ? "view" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "view":
                sender.sendMessage(ChatColor.GOLD + "Maces: " + ChatColor.WHITE
                        + plugin.getHeavyCoresFound() + "/" + plugin.getMaceLimit()
                        + ChatColor.GRAY + " currently exist.");
                return true;
            case "set":
                if (args.length < 2 || !args[1].matches("\\d+")) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maces set <number>");
                    return true;
                }
                plugin.setHeavyCoresFound(Integer.parseInt(args[1]));
                sender.sendMessage(ChatColor.GREEN + "Counter set to " + plugin.getHeavyCoresFound() + ".");
                return true;
            case "setlimit":
                if (args.length < 2 || !args[1].matches("\\d+")) {
                    sender.sendMessage(ChatColor.RED + "Usage: /maces setlimit <number>");
                    return true;
                }
                plugin.setMaceLimit(Integer.parseInt(args[1]));
                sender.sendMessage(ChatColor.GREEN + "Mace limit set to " + plugin.getMaceLimit() + ".");
                return true;
            case "reload":
                plugin.reloadSettings();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded. Limit is now " + plugin.getMaceLimit() + ".");
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /maces <view|set|setlimit|reload>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return new ArrayList<>(Arrays.asList("view", "set", "setlimit", "reload"));
        }
        return new ArrayList<>();
    }
}
