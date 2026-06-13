package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /tpa <player>     - ask to teleport TO them
 * /tpahere <player> - ask them to teleport TO you
 * /tpaccept [player] - accept a request
 * /tpdeny [player]   - deny a request
 */
public class TpaCommand implements CommandExecutor, TabCompleter {

    private final SummerSMPCore plugin;
    private final long expiryMillis;
    // key = target UUID (the person who must accept), value = the request
    private final Map<UUID, Request> pending = new HashMap<>();

    public TpaCommand(SummerSMPCore plugin) {
        this.plugin = plugin;
        this.expiryMillis = plugin.getConfig().getInt("tpa.request-seconds", 60) * 1000L;
    }

    private static class Request {
        final UUID requester;
        final boolean here; // true = requester wants target to come to them
        final long expiry;
        Request(UUID requester, boolean here, long expiry) {
            this.requester = requester;
            this.here = here;
            this.expiry = expiry;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }
        Player player = (Player) sender;
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa":      return request(player, args, false);
            case "tpahere":  return request(player, args, true);
            case "tpaccept": return accept(player, args);
            case "tpdeny":   return deny(player, args);
            default:         return false;
        }
    }

    private boolean request(Player player, String[] args, boolean here) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /" + (here ? "tpahere" : "tpa") + " <player>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("That player isn't online.", NamedTextColor.RED));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text("You can't teleport to yourself.", NamedTextColor.RED));
            return true;
        }

        pending.put(target.getUniqueId(), new Request(player.getUniqueId(), here, System.currentTimeMillis() + expiryMillis));

        if (here) {
            target.sendMessage(Component.text(player.getName() + " wants YOU to teleport to them.", NamedTextColor.AQUA));
        } else {
            target.sendMessage(Component.text(player.getName() + " wants to teleport to you.", NamedTextColor.AQUA));
        }
        target.sendMessage(Component.text("Type /tpaccept to allow, or /tpdeny to refuse.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Request sent to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean accept(Player player, String[] args) {
        Request req = pending.get(player.getUniqueId());
        if (req == null || req.expiry < System.currentTimeMillis()) {
            pending.remove(player.getUniqueId());
            player.sendMessage(Component.text("You have no pending teleport requests.", NamedTextColor.RED));
            return true;
        }
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester == null || !requester.isOnline()) {
            pending.remove(player.getUniqueId());
            player.sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED));
            return true;
        }

        // The person who will actually teleport.
        Player mover = req.here ? player : requester;
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isTagged(mover)) {
            player.sendMessage(Component.text("Can't teleport — someone is in combat.", NamedTextColor.RED));
            return true;
        }

        pending.remove(player.getUniqueId());
        if (req.here) {
            player.teleport(requester);
        } else {
            requester.teleport(player);
        }
        requester.sendMessage(Component.text(player.getName() + " accepted your teleport request.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Teleport accepted.", NamedTextColor.GREEN));
        return true;
    }

    private boolean deny(Player player, String[] args) {
        Request req = pending.remove(player.getUniqueId());
        if (req == null) {
            player.sendMessage(Component.text("You have no pending teleport requests.", NamedTextColor.RED));
            return true;
        }
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester != null) {
            requester.sendMessage(Component.text(player.getName() + " denied your teleport request.", NamedTextColor.RED));
        }
        player.sendMessage(Component.text("Request denied.", NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }
}
