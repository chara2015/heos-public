package heos;

import heos.config.HeosConfig;
import heos.storage.BanData;
import heos.storage.PlayerData;
import heos.storage.StoragePaths;
import heos.storage.WhitelistData;
import heos.utils.HeosLogger;
import heos.utils.LogFilterService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Main Heos class
 */
public class Heos {
    public static Path gameDirectory;

    private static HeosConfig config;
    private static BanData banData;
    private static WhitelistData whitelistData;
    private static final Map<String, PlayerData> playerDataCache = new HashMap<>();

    public static Path getHeosDirectory() {
        return StoragePaths.root();
    }

    public static HeosConfig getConfig() {
        if (config == null) {
            config = HeosConfig.load();
        }
        return config;
    }

    public static BanData getBanData() {
        if (banData == null) {
            banData = BanData.load();
        }
        return banData;
    }

    public static WhitelistData getWhitelistData() {
        if (whitelistData == null) {
            whitelistData = WhitelistData.load();
        }
        return whitelistData;
    }

    public static PlayerData getPlayerData(String username) {
        return getPlayerData(username, false);
    }

    public static PlayerData getPlayerData(String username, boolean onlineAccount) {
        String cacheKey = PlayerData.cacheKey(username, onlineAccount);
        return playerDataCache.computeIfAbsent(cacheKey, k -> PlayerData.load(username, onlineAccount));
    }

    public static void removePlayerData(String username) {
        invalidatePlayerData(username);
        PlayerData.delete(username);
    }

    public static void invalidatePlayerData(String username) {
        playerDataCache.remove(PlayerData.cacheKey(username, true));
        playerDataCache.remove(PlayerData.cacheKey(username, false));
        playerDataCache.remove(username.toLowerCase());
    }

    static void onStartServer(MinecraftServer server) {
        HeosLogger.info("=================================");
        HeosLogger.info("  _   _ _____    ___  ____  ");
        HeosLogger.info(" | | | | ____|  / _ \\/ ___| ");
        HeosLogger.info(" | |_| |  _|   | | | \\___ \\ ");
        HeosLogger.info(" |  _  | |___  | |_| |___) |");
        HeosLogger.info(" |_| |_|_____|  \\___/|____/ ");
        HeosLogger.info("=================================");
        HeosLogger.info("Mod id: " + Heosmod.MOD_ID);
        HeosLogger.info("Mod version: " + modVersion());
        HeosLogger.info("Mod author: " + Heosmod.MOD_AUTHOR);
        HeosLogger.info("Minecraft version: " + server.getServerVersion());

        StoragePaths.ensureRoot();
        HeosConfig.migrateLegacyConfig();
        BanData.migrateLegacyBanFile();
        WhitelistData.migrateLegacyWhitelistFile();
        PlayerData.initializeStorage();
        config = HeosConfig.load();
        if (config.updateRules) {
            int cleared = PlayerData.clearRuleAgreements();
            config.updateRules = false;
            config.save();
            HeosLogger.info("Rules updated; cleared " + cleared + " player rule agreements");
        }
        LogFilterService.installConfiguredFilters();
        banData = BanData.load();
        whitelistData = WhitelistData.load();

    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(Heosmod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(Heosmod.MOD_VERSION);
    }

    static void onStopServer(MinecraftServer server) {
        HeosLogger.info("=================================");
        HeosLogger.info("Shutting down Heos server...");
        HeosLogger.info("Clearing player data cache...");
        playerDataCache.clear();
        PlayerData.closeStorage();
        HeosLogger.info("Goodbye!");
        HeosLogger.info("=================================");
    }
}
