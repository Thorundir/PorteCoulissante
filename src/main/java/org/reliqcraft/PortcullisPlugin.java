package org.reliqcraft;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;

/**
 * PortcullisPlugin - Main plugin class for PorteCoulissante.
 * Handles configuration loading, event registration, and plugin lifecycle.
 */
public class PortcullisPlugin extends JavaPlugin {

    static final Logger logger = Logger.getLogger("Minecraft.org.reliqcraft");

    @Getter
    private boolean entityMovingEnabled;
    @Getter
    private int hoistingDelay, droppingDelay, soundEffectDistance, soundEffectVolume;
    @Getter
    private Set<Material> portcullisMaterials, powerBlocks, additionalWallMaterials;
    @Getter
    private boolean allowFloating, allPowerBlocksAllowed;
    @Getter
    private String startSoundURL, upSoundURL, downSoundURL;

    /**
     * Called when the plugin is disabled.
     * Logs shutdown event.
     */
    @Override
    public void onDisable() {
        logger.fine("[PorteCoulissante] Plugin disabled");
    }

    /**
     * Called when the plugin is enabled.
     * Loads configuration, initializes trace logging, registers events.
     */
    @Override
    public void onEnable() {
        TraceLogger.init(this);
        logger.log(Level.FINEST,
                "[PorteCoulissante] PortcullisPlugin.onEnable() (thread: " + Thread.currentThread() + ")",
                new Throwable());

        ensureConfigExists();
        loadConfiguration();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PortcullisBlockListener(this), this);

        logger.info("[PorteCoulissante] Plugin version " + getDescription().getVersion() + " by Captain_Chaos enabled");
        TraceLogger.testLogger();
        TraceLogger.value("Startup", "Loaded powerBlocks", getPowerBlocks(), TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Startup", "All power blocks allowed", isAllPowerBlocksAllowed(), TraceLogger.TraceLevel.DEBUG);
    }

    /**
     * Ensures config.yml exists by copying default.yml if missing.
     */
    private void ensureConfigExists() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            try (InputStream in = PortcullisPlugin.class.getResourceAsStream("/default.yml");
                    FileOutputStream out = new FileOutputStream(configFile)) {
                byte[] buffer = new byte[Defaults.BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                throw new RuntimeException("I/O error creating default config.yml file", e);
            }
        }
    }

    /**
     * Loads plugin configuration from config.yml.
     * Applies defaults and logs non-standard settings.
     */
    private void loadConfiguration() {
        FileConfiguration config = getConfig();

        entityMovingEnabled = config.getBoolean("entityMoving");
        hoistingDelay = config.getInt("hoistingDelay");
        droppingDelay = config.getInt("droppingDelay");

        // Trace logging level
        String traceLevelConfig = config.getString("trace-logging-level", "OFF").toUpperCase();
        try {
            TraceLogger.setLevel(TraceLogger.TraceLevel.valueOf(traceLevelConfig));
            logger.info("[PorteCoulissante] Trace logging level set to: " + traceLevelConfig);
        } catch (IllegalArgumentException e) {
            TraceLogger.setLevel(TraceLogger.TraceLevel.OFF);
            logger.warning("[PorteCoulissante] Invalid trace logging level '" + traceLevelConfig
                    + "' in config.yml. Defaulting to OFF.");
        }

        // Material sets with fallback to MaterialGroups
        portcullisMaterials = loadMaterialList("portcullisMaterials", MaterialGroups.PORTCULLIS);
        powerBlocks = loadMaterialList("powerBlocks", MaterialGroups.CONDUCTIVE);
        additionalWallMaterials = loadMaterialList("additionalWallMaterials", MaterialGroups.SUPPORTING);

        allowFloating = config.getBoolean("allowFloating");

        List<String> powerBlockNames = getConfig().getStringList("powerBlocks");
        allPowerBlocksAllowed = powerBlockNames.isEmpty();
        powerBlocks = powerBlockNames.isEmpty()
            ? MaterialGroups.CONDUCTIVE
            : parseMaterialList(powerBlockNames);


        startSoundURL = config.getString("startSoundURL");
        upSoundURL = config.getString("upSoundURL");
        downSoundURL = config.getString("downSoundURL");
        soundEffectDistance = config.getInt("soundEffectDistance");
        soundEffectVolume = config.getInt("soundEffectVolume");

        logNonStandardConfig(config);
        TraceLogger.value("Startup", "Loaded portcullis materials", getPortcullisMaterials(), TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Startup", "Loaded power blocks", getPowerBlocks(), TraceLogger.TraceLevel.DEBUG);

    }

    private Set<Material> parseMaterialList(List<String> names) {
        Set<Material> result = new HashSet<>();
        for (String name : names) {
            try {
                result.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                TraceLogger.value("Config", "Unknown material in config", name, TraceLogger.TraceLevel.BASIC);
            }
        }
        return result;
    }


    /**
     * Loads a material list from config with fallback to defaults.
     */
    private Set<Material> loadMaterialList(String key, Set<Material> fallback) {
        List<String> names = getConfig().getStringList(key);
        if (names.isEmpty())
            return fallback;

        Set<Material> result = new HashSet<>();
        for (String name : names) {
            try {
                result.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("[PorteCoulissante] Unknown material in config: " + name);
            }
        }
        return result;
    }

    /**
     * Logs warnings for non-standard configuration values.
     */
    private void logNonStandardConfig(FileConfiguration config) {
        List<String> warnings = new ArrayList<>();

        if (hoistingDelay != Defaults.HOISTING_DELAY)
            warnings.add("hoisting speed " + hoistingDelay);
        if (droppingDelay != Defaults.DROPPING_DELAY)
            warnings.add("dropping speed " + droppingDelay);
        if (!portcullisMaterials.equals(MaterialGroups.PORTCULLIS))
            warnings.add("portcullis materials " + portcullisMaterials);
        if (allowFloating != Defaults.ALLOW_FLOATING)
            warnings.add("floating not allowed");
        if (!allPowerBlocksAllowed)
            warnings.add("power blocks allowed " + powerBlocks);
        if (!additionalWallMaterials.isEmpty())
            warnings.add("additional wall materials " + additionalWallMaterials);

        if (!warnings.isEmpty()) {
            String joined = String.join(", ", warnings);
            logger.info("[PorteCoulissante] Non-standard configuration items loaded from config file: " + joined);
        }

        // Optional debug logging override
        String debugLogging = config.getString("debugLogging");
        if (debugLogging != null) {
            if (debugLogging.equalsIgnoreCase("extra")) {
                logger.setLevel(Level.FINEST);
                logger.info("[PorteCoulissante] Extra debug logging enabled (see log file)");
            } else if (!debugLogging.equalsIgnoreCase("false")) {
                logger.setLevel(Level.FINE);
                logger.info("[PorteCoulissante] Debug logging enabled (see log file)");
            }
        }
    }

    /**
     * Default configuration values.
     */
    public static class Defaults {
        public static final int HOISTING_DELAY = 40;
        public static final int DROPPING_DELAY = 10;
        public static final boolean ALLOW_FLOATING = true;
        public static final int BUFFER_SIZE = 32768;
    }
}