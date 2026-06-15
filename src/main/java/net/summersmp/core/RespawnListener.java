package net.summersmp.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Random;

/** Random respawn: if you have a bed/anchor set, use it; otherwise drop somewhere random in survival. */
public class RespawnListener implements Listener {

    private final SummerSMPCore plugin;
    private final Random random = new Random();

    public RespawnListener(SummerSMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Respect a bed or respawn anchor if the player has one.
        if (event.isBedSpawn() || event.isAnchorSpawn()) return;

        String worldName = plugin.getConfig().getString("rtp.world", "world");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) world = plugin.getServer().getWorlds().get(0);

        Location safe = findSafe(world);
        if (safe != null) {
            event.setRespawnLocation(safe);
        }
    }

    private Location findSafe(World world) {
        int radius = plugin.getConfig().getInt("rtp.radius", 5000);
        int minRadius = plugin.getConfig().getInt("rtp.min-radius", 100);
        for (int i = 0; i < 24; i++) {
            int x = randomCoord(radius, minRadius);
            int z = randomCoord(radius, minRadius);
            if (!world.getWorldBorder().isInside(new Location(world, x, 64, z))) continue;
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (isSafe(ground) && above.getType().isAir()) {
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

    private boolean isSafe(Block block) {
        Material t = block.getType();
        if (t == Material.LAVA || t == Material.WATER || t == Material.FIRE
                || t == Material.MAGMA_BLOCK || t == Material.CACTUS || t == Material.POWDER_SNOW) {
            return false;
        }
        return t.isSolid();
    }
}
