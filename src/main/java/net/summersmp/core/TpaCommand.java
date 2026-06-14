package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /tpa <player> / /tpahere <player> - send a request (15s send cooldown).
 * /tpaccept - accept; starts a 5s countdown before teleporting (cancels if hit).
 * /tpdeny - refuse a pending request.
 */
public class TpaCommand implements CommandExecutor, TabCompleter, Listener {

    private final SummerSMPCore plugin;
    private final long expiryMillis;
    private final Map<UUID, Request> pending = new HashMap<>();
    private final Map<UUID, Long> sendCooldown = new HashMap<>();
    private final Map<UUID, BukkitRunnable> warmups = new HashMap<>();

    public TpaCommand(SummerSMPCore plugin) {
        this.plugin = plugin;
        this.expiryMillis = plugin.getConfig().getInt("tpa.request-seconds", 60) * 1000L;
    }

    private static class Request {
        final UUID requester;
        final boolean here;
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
            case "tpaccept": return accept(player);
            case "tpdeny":   return deny(player);
            default:         return false;
        }
    }

    private boolean request(Player player, String[] args, boolean here) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /" + (here ? "tpahere" : "tpa") + " <player>", NamedTextColor.RED));
            return true;
        }
        // Send cooldown.
        long now = System.currentTimeMillis();
        Long until = sendCooldown.get(player.getUniqueId());
        if (until != null && until > now) {
            long secs = (until - now + 999) / 1000;
            player.sendMessage(Component.text("You can't send another teleport request for " + secs + " seconds.", NamedTextColor.RED));
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

        pending.put(target.getUniqueId(), new Request(player.getUniqueId(), here, now + expiryMillis));
        int cd = plugin.getConfig().getInt("tpa.send-cooldown-seconds", 15);
        sendCooldown.put(player.getUniqueId(), now + cd * 1000L);

        if (here) {
            target.sendMessage(Component.text(player.getName() + " wants YOU to teleport to them.", NamedTextColor.AQUA));
        } else {
            target.sendMessage(Component.text(player.getName() + " wants to teleport to you.", NamedTextColor.AQUA));
        }
        target.sendMessage(Component.text("Type /tpaccept to allow, or /tpdeny to refuse.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Request sent to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean accept(Player player) {
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

        final Player mover = req.here ? player : requester;
        final Player destination = req.here ? requester : player;
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isTagged(mover)) {
            player.sendMessage(Component.text("Can't teleport — someone is in combat.", NamedTextColor.RED));
            return true;
        }
        pending.remove(player.getUniqueId());

        int warmupSecs = plugin.getConfig().getInt("tpa.accept-warmup-seconds", 5);
        requester.sendMessage(Component.text(player.getName() + " accepted! Teleporting in " + warmupSecs + "s...", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Accepted! Teleporting in " + warmupSecs + "s...", NamedTextColor.GREEN));

        BukkitRunnable warmup = new BukkitRunnable() {
            int remaining = warmupSecs;
            @Override
            public void run() {
                if (!mover.isOnline() || !destination.isOnline()) {
                    cancel();
                    warmups.remove(mover.getUniqueId());
                    return;
                }
                if (remaining <= 0) {
                    cancel();
                    warmups.remove(mover.getUniqueId());
                    mover.teleport(destination);
                    mover.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
                    return;
                }
                mover.sendActionBar(Component.text("\u23F3 Teleporting in " + remaining + "s...", NamedTextColor.YELLOW));
                remaining--;
            }
        };
        warmups.put(mover.getUniqueId(), warmup);
        warmup.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    private boolean deny(Player player) {
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

    // Cancel a teleport warmup if the moving player takes damage.
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        UUID id = event.getEntity().getUniqueId();
        BukkitRunnable warmup = warmups.remove(id);
        if (warmup != null) {
            warmup.cancel();
            Player player = (Player) event.getEntity();
            player.sendActionBar(Component.text(""));
            player.sendMessage(Component.text("Teleport cancelled — you took damage.", NamedTextColor.RED));
        }
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
