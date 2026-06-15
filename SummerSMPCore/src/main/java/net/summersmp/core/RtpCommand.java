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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** /rtp - random teleport. Has a cooldown and is blocked while in combat. */
public class RtpCommand implements CommandExecutor {

    private final SummerSMPCore plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

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

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isTagged(player)) {
            player.sendMessage(Component.text("You can't random-teleport while in combat!", NamedTextColor.RED));
            return true;
        }

        int cooldown = plugin.getConfig().getInt("rtp.cooldown-seconds", 30);
        boolean bypass = player.hasPermission("summersmp.rtp.nocooldown");
        long now = System.currentTimeMillis();
        if (!bypass) {
            Long until = cooldownUntil.get(player.getUniqueId());
            if (until != null && until > now) {
                long secs = (until - now + 999) / 1000;
                player.sendMessage(Component.text("Wait " + secs + "s before using /rtp again.", NamedTextColor.RED));
                return true;
            }
        }

        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            world = plugin.getServer().getWorlds().get(0);
        }

        int radius = plugin.getConfig().getInt("rtp.radius", 5000);
        int minRadius = plugin.getConfig().getInt("rtp.min-radius", 100);
        int attempts = plugin.getConfig().getInt("rtp.max-attempts", 24);

        player.sendMessage(Component.text("Finding a safe spot...", NamedTextColor.GRAY));
        Location dest = findSafeLocation(world, radius, minRadius, attempts);
        if (dest == null) {
            player.sendMessage(Component.text("Couldn't find a safe spot. Try again!", NamedTextColor.RED));
            return true;
        }

        player.teleport(dest);
        player.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
        if (!bypass) {
            cooldownUntil.put(player.getUniqueId(), now + cooldown * 1000L);
        }
        return true;
    }

    private Location findSafeLocation(World world, int radius, int minRadius, int attempts) {
        for (int i = 0; i < attempts; i++) {
            int x = randomCoord(radius, minRadius);
            int z = randomCoord(radius, minRadius);
            if (!world.getWorldBorder().isInside(new Location(world, x, 64, z))) continue;
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (isSafeGround(ground) && above.getType().isAir()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
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
