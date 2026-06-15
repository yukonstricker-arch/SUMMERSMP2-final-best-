package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /world - teleport to 0,0 in the survival world (not the hub). */
public class WorldCommand implements CommandExecutor {

    private final SummerSMPCore plugin;

    public WorldCommand(SummerSMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /world.");
            return true;
        }
        Player player = (Player) sender;

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isTagged(player)) {
            player.sendMessage(Component.text("You can't teleport while in combat!", NamedTextColor.RED));
            return true;
        }

        String worldName = plugin.getConfig().getString("rtp.world", "world");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            world = plugin.getServer().getWorlds().get(0);
        }

        int y = world.getHighestBlockYAt(0, 0);
        Location dest = new Location(world, 0.5, y + 1, 0.5);
        player.teleport(dest);
        player.sendMessage(Component.text("Teleported to the center of the world (0, 0).", NamedTextColor.GREEN));
        return true;
    }
}
