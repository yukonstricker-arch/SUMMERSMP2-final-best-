package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** /rtp - 5s warmup, async-loads the destination so it's never laggy, then a cooldown. */
public class RtpCommand implements CommandExecutor, Listener {

    private final SummerSMPCore plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, BukkitRunnable> warmups = new HashMap<>();

    public RtpCommand(SummerSMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /rtp.");
            return true;
        }
        Player player = (Player) sender;
        UUID id = player.getUniqueId();

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isTagged(player)) {
            player.sendMessage(Component.text("You can't random-teleport while in combat!", NamedTextColor.RED));
            return true;
        }
        if (warmups.containsKey(id)) {
            player.sendMessage(Component.text("You're already teleporting — hold still!", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("summersmp.rtp.nocooldown")) {
            long now = System.currentTimeMillis();
            Long until = cooldownUntil.get(id);
            if (until != null && until > now) {
                long secs = (until - now + 999) / 1000;
                player.sendMessage(Component.text("You can't RTP for another " + secs + " seconds.", NamedTextColor.RED));
                return true;
            }
        }

        int warmupSecs = plugin.getConfig().getInt("rtp.warmup-seconds", 5);
        player.sendMessage(Component.text("Teleporting in " + warmupSecs + " seconds... stand still.", NamedTextColor.GRAY));

        BukkitRunnable warmup = new BukkitRunnable() {
            int remaining = warmupSecs;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    warmups.remove(id);
                    return;
                }
                if (remaining <= 0) {
                    cancel();
                    warmups.remove(id);
                    doRtp(player);
                    return;
                }
                player.sendActionBar(Component.text("\u23F3 Teleporting in " + remaining + "s...", NamedTextColor.YELLOW));
                remaining--;
            }
        };
        warmups.put(id, warmup);
        warmup.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    // Cancel the warmup if the player takes damage.
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

    private World resolveWorld(Player player) {
        String configured = plugin.getConfig().getString("rtp.world", "world");
        if (configured != null && !configured.isEmpty()) {
            World w = plugin.getServer().getWorld(configured);
            if (w != null) return w;
        }
        if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
            return player.getWorld();
        }
        return plugin.getServer().getWorlds().get(0);
    }

    private void doRtp(Player player) {
        World world = resolveWorld(player);
        int attempts = plugin.getConfig().getInt("rtp.max-attempts", 24);
        player.sendMessage(Component.text("Finding a safe spot...", NamedTextColor.GRAY));
        tryTeleport(player, world, attempts);
    }

    private void tryTeleport(Player player, World world, int attemptsLeft) {
        if (!player.isOnline()) return;
        int radius = plugin.getConfig().getInt("rtp.radius", 5000);
        int minRadius = plugin.getConfig().getInt("rtp.min-radius", 100);

        int x = randomCoord(radius, minRadius);
        int z = randomCoord(radius, minRadius);
        if (!world.getWorldBorder().isInside(new Location(world, x, 64, z))) {
            if (attemptsLeft > 0) tryTeleport(player, world, attemptsLeft - 1);
            else fail(player);
            return;
        }

        // Load the chunk off the main thread so the teleport doesn't lag the server.
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                int y = world.getHighestBlockYAt(x, z);
                Block ground = world.getBlockAt(x, y, z);
                Block above = world.getBlockAt(x, y + 1, z);
                if (isSafeGround(ground) && above.getType().isAir()) {
                    player.teleport(new Location(world, x + 0.5, y + 1, z + 0.5));
                    player.sendMessage(Component.text("Teleported to " + x + ", " + (y + 1) + ", " + z + "!", NamedTextColor.GREEN));
                    if (!player.hasPermission("summersmp.rtp.nocooldown")) {
                        int cooldown = plugin.getConfig().getInt("rtp.cooldown-seconds", 15);
                        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);
                    }
                } else if (attemptsLeft > 0) {
                    tryTeleport(player, world, attemptsLeft - 1);
                } else {
                    fail(player);
                }
            })
        );
    }

    private void fail(Player player) {
        player.sendMessage(Component.text("Couldn't find a safe spot. Try again!", NamedTextColor.RED));
    }

    private int randomCoord(int radius, int minRadius) {
        int span = Math.max(1, radius - minRadius);
        int value = minRadius + random.nextInt(span);
        return random.nextBoolean() ? value : -value;
    }

    private boolean isSafeGround(Block block) {
        Material t = block.getType();
        if (t == Material.LAVA || t == Material.WATER || t == Material.FIRE
                || t == Material.MAGMA_BLOCK || t == Material.CACTUS || t == Material.POWDER_SNOW) {
            return false;
        }
        return t.isSolid();
    }
}
