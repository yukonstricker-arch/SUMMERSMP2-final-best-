package net.summersmp.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Caps how many Maces / Heavy Cores exist at once (default 3).
 *  - Picking up a new heavy core counts it (up to the limit; extras vanish).
 *  - Destroying a counted mace/heavy core frees a slot (counter -1).
 *  - Placing then breaking a heavy core does NOT re-count it (bug fix).
 */
public class MaceLimitListener implements Listener {

    private final SummerSMPCore plugin;

    public MaceLimitListener(SummerSMPCore plugin) {
        this.plugin = plugin;
    }

    private boolean isRestricted(Material m) {
        return m == Material.HEAVY_CORE || m == Material.MACE;
    }

    private boolean isCounted(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(plugin.countedKey, PersistentDataType.BYTE);
    }

    private ItemStack tagAsCounted(ItemStack stack) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(plugin.countedKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ---------- Pick up a heavy core: count it, or block the 4th ----------

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();
        if (stack.getType() != Material.HEAVY_CORE) return;
        if (isCounted(stack)) return; // already part of the count — let it through

        Player player = event.getPlayer();
        if (plugin.getHeavyCoresFound() >= plugin.getMaceLimit()) {
            event.setCancelled(true);
            itemEntity.remove();
            if (player != null) {
                player.sendMessage(ChatColor.RED + "This server is at its limit of "
                        + plugin.getMaceLimit() + " Maces, so this Heavy Core crumbles to dust.");
            }
            return;
        }

        plugin.incrementHeavyCores();
        itemEntity.setItemStack(tagAsCounted(stack));
        announce(ChatColor.GOLD + "" + ChatColor.BOLD + "A Heavy Core has been claimed! "
                + ChatColor.YELLOW + "(" + plugin.getHeavyCoresFound() + "/" + plugin.getMaceLimit() + ")");
    }

    // ---------- Crafting a mace keeps the "counted" tag (no count change) ----------

    @EventHandler(ignoreCancelled = true)
    public void onCraftMace(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.MACE) return;
        // A mace can only come from a heavy core, which is always counted, so tag the mace.
        event.getInventory().setResult(tagAsCounted(result));
    }

    // ---------- Place / break a heavy core block (bug fix: no re-count) ----------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HEAVY_CORE) return;
        if (isCounted(event.getItemInHand())) {
            // Remember this block IS a counted heavy core, so breaking it won't re-count.
            plugin.addCountedBlock(event.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (event.getBlockState().getType() != Material.HEAVY_CORE) return;
        if (!plugin.isCountedBlock(event.getBlock())) return;
        // Re-tag the dropped item so picking it back up doesn't count again.
        for (Item dropped : event.getItems()) {
            dropped.setItemStack(tagAsCounted(dropped.getItemStack()));
        }
        plugin.removeCountedBlock(event.getBlock());
    }

    // ---------- Destruction frees a slot (counter -1) ----------

    private void destroyOne(String how) {
        plugin.decrementHeavyCores();
        announce(ChatColor.RED + "A Mace was destroyed " + how + "! "
                + ChatColor.GRAY + "(" + plugin.getHeavyCoresFound() + "/" + plugin.getMaceLimit() + " remain)");
    }

    // Item burned in lava/fire, blown up, hit by cactus, etc.
    @EventHandler(ignoreCancelled = true)
    public void onItemDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item)) return;
        Item item = (Item) event.getEntity();
        if (!isCounted(item.getItemStack())) return;
        if (item.getPersistentDataContainer().has(plugin.decrementedKey, PersistentDataType.BYTE)) return;

        // Will this damage destroy the item?
        if (event.getFinalDamage() >= item.getHealth()) {
            item.getPersistentDataContainer().set(plugin.decrementedKey, PersistentDataType.BYTE, (byte) 1);
            destroyOne("in the world");
        }
    }

    // Dropped item despawned after 5 minutes.
    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (isCounted(event.getEntity().getItemStack())) {
            destroyOne("(despawned)");
        }
    }

    // A mace wore out and broke.
    @EventHandler(ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        ItemStack broken = event.getBrokenItem();
        if (broken.getType() == Material.MACE && isCounted(broken)) {
            destroyOne("(worn out)");
        }
    }

    // A counted heavy-core BLOCK destroyed by an explosion.
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplodedBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplodedBlocks(event.blockList());
    }

    private void handleExplodedBlocks(java.util.List<org.bukkit.block.Block> blocks) {
        for (org.bukkit.block.Block b : blocks) {
            if (b.getType() == Material.HEAVY_CORE && plugin.isCountedBlock(b)) {
                plugin.removeCountedBlock(b);
                destroyOne("in an explosion");
            }
        }
    }

    private void announce(String msg) {
        if (plugin.announceClaims()) {
            Bukkit.broadcastMessage(msg);
        }
    }
}
