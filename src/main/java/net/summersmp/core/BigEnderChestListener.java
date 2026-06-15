package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** A 54-slot (double-chest) ender chest, saved per player. Heavy Cores / Maces still can't go in. */
public class BigEnderChestListener implements Listener {

    private final SummerSMPCore plugin;
    private final File folder;

    public BigEnderChestListener(SummerSMPCore plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "enderchests");
        if (!folder.exists()) folder.mkdirs();
    }

    /** Marks an inventory as one of our big ender chests. */
    public static class Holder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;
        public Holder(UUID owner) {
            this.owner = owner;
        }
        public UUID getOwner() {
            return owner;
        }
        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("big-ender-chest.enabled", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENDER_CHEST) return;
        // Let players place blocks while sneaking instead of opening.
        if (event.getPlayer().isSneaking() && event.getItem() != null && event.getItem().getType().isBlock()) return;

        event.setCancelled(true);
        open(event.getPlayer());
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Ender Chest"));
        holder.setInventory(inv);
        load(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Holder) {
            save(((Holder) holder).getOwner(), event.getInventory());
        }
    }

    // ----- Heavy Core / Mace restriction -----

    private boolean isRestricted(ItemStack stack) {
        if (stack == null) return false;
        Material m = stack.getType();
        return m == Material.HEAVY_CORE || m == Material.MACE;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        if (event.isShiftClick() && rawSlot >= topSize) {
            if (isRestricted(event.getCurrentItem())) deny(event);
            return;
        }
        if (rawSlot >= 0 && rawSlot < topSize) {
            if (isRestricted(event.getCursor())) {
                deny(event);
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int button = event.getHotbarButton();
                if (button >= 0 && isRestricted(event.getView().getBottomInventory().getItem(button))) {
                    deny(event);
                    return;
                }
            }
            if (event.getClick() == ClickType.SWAP_OFFHAND && event.getWhoClicked() instanceof Player) {
                if (isRestricted(((Player) event.getWhoClicked()).getInventory().getItemInOffHand())) deny(event);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        if (!isRestricted(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                message(event.getWhoClicked());
                return;
            }
        }
    }

    private void deny(InventoryClickEvent event) {
        event.setCancelled(true);
        message(event.getWhoClicked());
    }

    private void message(HumanEntity who) {
        who.sendMessage(Component.text("You can't store Heavy Cores or Maces in an ender chest.", NamedTextColor.RED));
    }

    // ----- persistence -----

    private File file(UUID id) {
        return new File(folder, id.toString() + ".yml");
    }

    private void load(UUID id, Inventory inv) {
        File file = file(id);
        if (!file.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (int i = 0; i < inv.getSize(); i++) {
            if (config.contains("slot." + i)) {
                inv.setItem(i, config.getItemStack("slot." + i));
            }
        }
    }

    private void save(UUID id, Inventory inv) {
        FileConfiguration config = new YamlConfiguration();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) config.set("slot." + i, item);
        }
        try {
            config.save(file(id));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save ender chest for " + id + ": " + e.getMessage());
        }
    }
}
