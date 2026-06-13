package net.summersmp.core;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * /ban <player> <duration> [reason]  e.g. "/ban Steve 12 days", "/ban Steve 7d cheating", "/ban Steve perm"
 * /unban <player>
 */
public class BanCommand implements CommandExecutor, TabCompleter {

    private final SummerSMPCore plugin;

    public BanCommand(SummerSMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("unban")) {
            return handleUnban(sender, args);
        }
        return handleBan(sender, args);
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("summersmp.ban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ban <player> <duration> [reason]");
            sender.sendMessage(ChatColor.GRAY + "Examples: /ban Steve 12 days  |  /ban Steve 7d cheating  |  /ban Steve perm");
            return true;
        }

        String targetName = args[0];
        long durationMillis;
        int reasonStart;

        String first = args[1].toLowerCase(Locale.ROOT);
        if (first.equals("perm") || first.equals("permanent") || first.equals("forever")) {
            durationMillis = -1;
            reasonStart = 2;
        } else if (first.matches("\\d+") && args.length >= 3 && unitToMillisPer(args[2]) > 0) {
            durationMillis = Long.parseLong(first) * unitToMillisPer(args[2]);
            reasonStart = 3;
        } else {
            Long compact = parseCompact(args[1]);
            if (compact == null) {
                sender.sendMessage(ChatColor.RED + "Couldn't read the duration \"" + args[1]
                        + "\". Try: 30 minutes, 12 days, 7d, 2w, perm.");
                return true;
            }
            durationMillis = compact;
            reasonStart = 2;
        }

        String reason = (args.length > reasonStart)
                ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length))
                : plugin.defaultBanReason();

        Date expires = (durationMillis < 0) ? null : new Date(System.currentTimeMillis() + durationMillis);
        String source = (sender instanceof Player) ? sender.getName() : "Console";

        applyBan(targetName, reason, expires, source);

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            String kick = ChatColor.RED + "" + ChatColor.BOLD + "You have been banned.\n\n"
                    + ChatColor.GRAY + "Reason: " + ChatColor.WHITE + reason + "\n"
                    + ChatColor.GRAY + (expires == null
                        ? "Duration: " + ChatColor.WHITE + "Permanent"
                        : "Expires: " + ChatColor.WHITE + formatDate(expires));
            online.kickPlayer(kick);
        }

        sender.sendMessage(ChatColor.GREEN + "Banned " + ChatColor.WHITE + targetName + ChatColor.GREEN
                + (expires == null ? " permanently." : " until " + ChatColor.WHITE + formatDate(expires) + ChatColor.GREEN + ".")
                + ChatColor.GRAY + " Reason: " + reason);
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("summersmp.unban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
            return true;
        }
        removeBan(args[0]);
        sender.sendMessage(ChatColor.GREEN + "Unbanned " + ChatColor.WHITE + args[0] + ChatColor.GREEN + ".");
        return true;
    }

    @SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
    private void applyBan(String name, String reason, Date expires, String source) {
        BanList list = Bukkit.getBanList(BanList.Type.NAME);
        list.addBan(name, reason, expires, source);
    }

    @SuppressWarnings({"deprecation", "rawtypes"})
    private void removeBan(String name) {
        BanList list = Bukkit.getBanList(BanList.Type.NAME);
        list.pardon(name);
    }

    private long unitToMillisPer(String unitRaw) {
        String u = unitRaw.toLowerCase(Locale.ROOT);
        if (u.equals("s") || u.startsWith("sec")) return 1000L;
        if (u.equals("m") || u.startsWith("min")) return 60_000L;
        if (u.equals("h") || u.startsWith("hour") || u.equals("hr") || u.equals("hrs")) return 3_600_000L;
        if (u.equals("d") || u.startsWith("day")) return 86_400_000L;
        if (u.equals("w") || u.startsWith("week")) return 604_800_000L;
        if (u.equals("mo") || u.startsWith("month")) return 2_592_000_000L;
        if (u.equals("y") || u.startsWith("year")) return 31_536_000_000L;
        return -1;
    }

    private Long parseCompact(String token) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d+)\\s*([a-zA-Z]+)$").matcher(token.trim());
        if (!matcher.matches()) return null;
        long amount = Long.parseLong(matcher.group(1));
        long per = unitToMillisPer(matcher.group(2));
        if (per <= 0) return null;
        return amount * per;
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm z").format(date);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
            return out;
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("ban")) {
            return new ArrayList<>(Arrays.asList("7d", "12", "30m", "perm"));
        }
        if (args.length == 3 && command.getName().equalsIgnoreCase("ban") && args[1].matches("\\d+")) {
            return new ArrayList<>(Arrays.asList("minutes", "hours", "days", "weeks", "months"));
        }
        return out;
    }
}
