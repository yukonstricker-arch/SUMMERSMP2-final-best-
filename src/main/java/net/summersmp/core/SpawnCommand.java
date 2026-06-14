package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /spawn - teleport to the hub spawn. */
public class SpawnCommand implements CommandExecutor {

    private final SummerSMPCore plugin;

    public SpawnCommand(SummerSMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /spawn.");
            return true;
        }
        Player player = (Player) sender;

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isTagged(player)) {
            player.sendMessage(Component.text("You can't go to spawn while in combat!", NamedTextColor.RED));
            return true;
        }

        String worldName = plugin.getConfig().getString("spawn-world", "hub");
        World hub = plugin.getServer().getWorld(worldName);
        if (hub == null) {
            player.sendMessage(Component.text("The spawn world isn't loaded. Tell an admin.", NamedTextColor.RED));
            return true;
        }

        player.teleport(hub.getSpawnLocation());
        player.sendMessage(Component.text("Welcome back to spawn!", NamedTextColor.GREEN));
        return true;
    }
}
