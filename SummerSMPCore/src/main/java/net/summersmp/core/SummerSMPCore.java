package net.summersmp.core;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class SummerSMPCore extends JavaPlugin {

    private int maceLimit;
    private int heavyCoresFound;
    private final Set<String> countedBlocks = new HashSet<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    /** Marks a heavy core / mace ITEM that has been counted toward the limit. */
    public NamespacedKey countedKey;
    /** Marks an ITEM ENTITY we've already removed from the count (avoids double-decrement). */
    public NamespacedKey decrementedKey;

    private CombatManager combatManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        countedKey = new NamespacedKey(this, "counted_mace");
        decrementedKey = new NamespacedKey(this, "decremented_mace");
        loadData();

        try {
            Bukkit.removeRecipe(NamespacedKey.minecraft("end_crystal"));
        } catch (Throwable t) {
            getLogger().warning("Could not remove the end crystal recipe: " + t.getMessage());
        }

        // Listeners
        getServer().getPluginManager().registerEvents(new EndCrystalListener(), this);
        getServer().getPluginManager().registerEvents(new MaceLimitListener(this), this);
        getServer().getPluginManager().registerEvents(new EnderChestListener(), this);

        combatManager = new CombatManager(this);
        getServer().getPluginManager().registerEvents(combatManager, this);
        combatManager.runTaskTimer(this, 20L, 20L);

        // Commands
        BanCommand banCommand = new BanCommand(this);
        setExecutor("ban", banCommand, banCommand);
        setExecutor("unban", banCommand, banCommand);

        MaceCommand maceCommand = new MaceCommand(this);
        setExecutor("maces", maceCommand, maceCommand);

        RtpCommand rtpCommand = new RtpCommand(this);
        setExecutor("rtp", rtpCommand, null);

        TpaCommand tpaCommand = new TpaCommand(this);
        setExecutor("tpa", tpaCommand, tpaCommand);
        setExecutor("tpahere", tpaCommand, tpaCommand);
        setExecutor("tpaccept", tpaCommand, null);
        setExecutor("tpdeny", tpaCommand, null);

        getLogger().info("Enabled. Mace limit: " + maceLimit + " | currently counted: " + heavyCoresFound);
    }

    private void setExecutor(String name, org.bukkit.command.CommandExecutor exec,
                             org.bukkit.command.TabCompleter tab) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(exec);
            if (tab != null) getCommand(name).setTabCompleter(tab);
        } else {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml!");
        }
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public void reloadSettings() {
        reloadConfig();
        maceLimit = getConfig().getInt("mace-limit", 3);
    }

    // ----- data file -----

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create data.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        heavyCoresFound = dataConfig.getInt("heavy-cores-found", 0);
        countedBlocks.clear();
        countedBlocks.addAll(dataConfig.getStringList("counted-blocks"));
    }

    private void saveData() {
        if (dataConfig == null || dataFile == null) return;
        dataConfig.set("heavy-cores-found", heavyCoresFound);
        dataConfig.set("counted-blocks", new java.util.ArrayList<>(countedBlocks));
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    // ----- settings -----

    public int getMaceLimit() {
        return maceLimit;
    }

    public void setMaceLimit(int value) {
        maceLimit = Math.max(0, value);
        getConfig().set("mace-limit", maceLimit);
        saveConfig();
    }

    public boolean announceClaims() {
        return getConfig().getBoolean("announce-heavy-core-claims", true);
    }

    public String defaultBanReason() {
        return getConfig().getString("default-ban-reason", "Banned by staff");
    }

    // ----- counter -----

    public int getHeavyCoresFound() {
        return heavyCoresFound;
    }

    public void setHeavyCoresFound(int value) {
        heavyCoresFound = Math.max(0, value);
        saveData();
    }

    public void incrementHeavyCores() {
        setHeavyCoresFound(heavyCoresFound + 1);
    }

    public void decrementHeavyCores() {
        setHeavyCoresFound(heavyCoresFound - 1);
    }

    // ----- counted heavy-core BLOCKS (so place+break doesn't re-count) -----

    private String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public boolean isCountedBlock(Block b) {
        return countedBlocks.contains(key(b));
    }

    public void addCountedBlock(Block b) {
        countedBlocks.add(key(b));
        saveData();
    }

    public void removeCountedBlock(Block b) {
        countedBlocks.remove(key(b));
        saveData();
    }
}
