package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Combat tagging. Hitting / being hit by another player starts a countdown.
 * While tagged you cannot log out (you die if you do) and cannot use escape commands.
 */
public class CombatManager extends BukkitRunnable implements Listener {

    private final SummerSMPCore plugin;
    private final int tagSeconds;
    private final Set<String> blockedCommands = new HashSet<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();

    public CombatManager(SummerSMPCore plugin) {
        this.plugin = plugin;
        this.tagSeconds = plugin.getConfig().getInt("combat.tag-seconds", 20);
        List<String> list = plugin.getConfig().getStringList("combat.blocked-commands");
        for (String c : list) {
            blockedCommands.add(c.toLowerCase(Locale.ROOT).replace("/", ""));
        }
    }

    public boolean isTagged(Player player) {
        Long until = combatUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public int secondsLeft(Player player) {
        Long until = combatUntil.get(player.getUniqueId());
        if (until == null) return 0;
        long ms = until - System.currentTimeMillis();
        return ms <= 0 ? 0 : (int) Math.ceil(ms / 1000.0);
    }

    private void tag(Player player) {
        boolean wasTagged = isTagged(player);
        combatUntil.put(player.getUniqueId(), System.currentTimeMillis() + tagSeconds * 1000L);
        if (!wasTagged) {
            player.sendMessage(Component.text("\u2694 You are now in combat! Don't log out for "
                    + tagSeconds + " seconds.", NamedTextColor.RED));
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;
        tag(victim);
        tag(attacker);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isTagged(player)) {
            combatUntil.remove(player.getUniqueId());
            player.setHealth(0.0);
            Bukkit.broadcast(Component.text(player.getName()
                    + " logged out in combat and died!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        combatUntil.remove(event.getEntity().getUniqueId());
    }

    // Block escape commands while tagged.
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isTagged(player)) return;

        String raw = event.getMessage();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String label = raw.split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);

        if (blockedCommands.contains(label)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("\u2694 You can't use that command while in combat! ("
                    + secondsLeft(player) + "s left)", NamedTextColor.RED));
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        combatUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            int left = secondsLeft(player);
            if (left > 0) {
                player.sendActionBar(Component.text("\u2694 Combat: " + left + "s", NamedTextColor.RED));
            }
        }
    }
}
