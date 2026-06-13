package net.summersmp.core;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/** Heavy Cores and Maces can't be stored in ender chests. */
public class EnderChestListener implements Listener {

    private boolean isRestricted(ItemStack stack) {
        if (stack == null) return false;
        Material m = stack.getType();
        return m == Material.HEAVY_CORE || m == Material.MACE;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.ENDER_CHEST) return;

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
                if (button >= 0) {
                    ItemStack hotbar = event.getView().getBottomInventory().getItem(button);
                    if (isRestricted(hotbar)) {
                        deny(event);
                        return;
                    }
                }
            }
            if (event.getClick() == ClickType.SWAP_OFFHAND && event.getWhoClicked() instanceof Player) {
                ItemStack offhand = ((Player) event.getWhoClicked()).getInventory().getItemInOffHand();
                if (isRestricted(offhand)) deny(event);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (!isRestricted(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED
                        + "You can't store Heavy Cores or Maces in an ender chest.");
                return;
            }
        }
    }

    private void deny(InventoryClickEvent event) {
        event.setCancelled(true);
        event.getWhoClicked().sendMessage(ChatColor.RED
                + "You can't store Heavy Cores or Maces in an ender chest.");
    }
}
