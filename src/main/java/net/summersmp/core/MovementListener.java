package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Disables elytra gliding and configured movement items (e.g. chorus fruit). */
public class MovementListener implements Listener {

    private final SummerSMPCore plugin;
    private final boolean disableElytra;
    private final Set<Material> blocked = new HashSet<>();

    public MovementListener(SummerSMPCore plugin) {
        this.plugin = plugin;
        this.disableElytra = plugin.getConfig().getBoolean("disable-elytra", true);
        List<String> items = plugin.getConfig().getStringList("disabled-movement-items");
        for (String name : items) {
            Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (m != null) blocked.add(m);
            else plugin.getLogger().warning("Unknown disabled-movement-item: " + name);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!disableElytra) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isGliding()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (blocked.contains(event.getItem().getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("That item is disabled on this server.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item != null && blocked.contains(item.getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("That item is disabled on this server.", NamedTextColor.RED));
        }
    }
}
