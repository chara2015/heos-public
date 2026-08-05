package heos.folia;

import heos.folia.commands.FoliaAdminCommands;
import heos.folia.commands.FoliaAuthCommands;
import heos.folia.event.FoliaAuthListener;
import heos.folia.event.FoliaAuthService;
import heos.folia.commands.FoliaBanCommands;
import heos.folia.storage.FoliaBanData;
import heos.folia.commands.FoliaMigrationCommands;
import heos.folia.event.FoliaCommandInterceptor;
import heos.folia.integrations.FoliaRecipeSyncService;
import heos.folia.rules.FoliaRuleAgreementService;
import heos.folia.storage.FoliaStorage;
import heos.folia.storage.FoliaWhitelistData;
import heos.folia.utils.FoliaLoginUsernameValidationBypassService;
import heos.folia.utils.FoliaTpsDisplayService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class HeosFoliaPlugin extends JavaPlugin {
    private static final String MOD_AUTHOR = "chara201x, chatgpt, claude";
    private static final Map<String, String> CONFIG_ALIASES = Map.ofEntries(
            Map.entry("enableAuthentication", "auth.enabled"),
            Map.entry("language", "auth.language"),
            Map.entry("allowOfflinePlayers", "offline.allow-players"),
            Map.entry("allowMoreOfflineUsernameCharacters", "offline.allow-more-username-characters"),
            Map.entry("allowUnicodeOfflineUsernameCharacters", "offline.allow-unicode-username-characters"),
            Map.entry("loginTimeout", "login.timeout-seconds"),
            Map.entry("loginReminderSeconds", "login.reminder-seconds"),
            Map.entry("minPasswordLength", "login.min-password-length"),
            Map.entry("maxPasswordLength", "login.max-password-length"),
            Map.entry("maxConcurrentSessionsPerIp", "security.max-sessions-per-ip"),
            Map.entry("sessionLimitKickMessage", "security.session-limit-kick-message"),
            Map.entry("enableUsernameLoginFailureLock", "security.username-failure-lock"),
            Map.entry("usernameLoginFailureLimit", "security.username-failure-limit"),
            Map.entry("usernameLoginFailureLockSeconds", "security.username-failure-lock-seconds"),
            Map.entry("enableIpLoginFailureLock", "security.ip-failure-lock"),
            Map.entry("ipLoginFailureLimit", "security.ip-failure-limit"),
            Map.entry("ipLoginFailureLockSeconds", "security.ip-failure-lock-seconds"),
            Map.entry("enableWhitelist", "features.whitelist"),
            Map.entry("enableCustomBan", "features.custom-ban"),
            Map.entry("enablePlayerDataMigration", "features.player-data-migration"),
            Map.entry("migrationBanSeconds", "features.migration-ban-seconds"),
            Map.entry("enableAutoLogTps", "integrations.tps-footer"),
            Map.entry("autoLogTpsDelayTicks", "integrations.tps-footer-delay-ticks"),
            Map.entry("enableLogFilter", "integrations.log-filter"),
            Map.entry("enableRecipeViewerSync", "integrations.recipe-viewer-sync")
    );

    private FoliaAuthService authService;
    private FoliaStorage storage;
    private FoliaBanData banData;
    private FoliaWhitelistData whitelistData;
    private FoliaTpsDisplayService tpsDisplayService;
    private FoliaRecipeSyncService recipeSyncService;
    private FoliaLoginUsernameValidationBypassService usernameValidationBypassService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyConfigAliases();
        heos.folia.utils.FoliaMessages.init(this);
        heos.folia.utils.FoliaLogFilterService.installConfiguredFilters(this);
        this.storage = new FoliaStorage(getDataFolder().toPath());
        storage.initialize();
        FoliaRuleAgreementService rulesService = new FoliaRuleAgreementService(this, storage);
        if (getConfig().getBoolean("rules.update", false)) {
            int cleared = storage.clearRuleAgreements();
            getConfig().set("rules.update", false);
            saveConfig();
            getLogger().info("Rules updated; cleared " + cleared + " player rule agreements");
        }
        this.banData = FoliaBanData.load(getDataFolder().toPath(), getLogger());
        this.whitelistData = FoliaWhitelistData.load(getDataFolder().toPath(), getLogger());
        this.tpsDisplayService = new FoliaTpsDisplayService(this);
        this.usernameValidationBypassService = new FoliaLoginUsernameValidationBypassService(this, banData, whitelistData);
        usernameValidationBypassService.install();

        this.authService = new FoliaAuthService(this, storage, tpsDisplayService);
        authService.setRulesService(rulesService);
        FoliaBanCommands banCommands = new FoliaBanCommands(banData, storage);
        FoliaMigrationCommands migrationCommands = new FoliaMigrationCommands(this, storage, banData);
        getServer().getPluginManager().registerEvents(new FoliaCommandInterceptor(authService, rulesService, banCommands, migrationCommands), this);
        getServer().getPluginManager().registerEvents(new FoliaAuthListener(this, authService, rulesService, banData, whitelistData), this);
        registerCommands(banCommands, migrationCommands);
        boolean recipeViewerSyncEnabled = isRecipeViewerSyncEnabled();
        if (recipeViewerSyncEnabled) {
            this.recipeSyncService = new FoliaRecipeSyncService(this);
        }

        logStartupBanner();
    }

    private void logStartupBanner() {
        getLogger().info("=================================");
        getLogger().info("  _   _ _____    ___  ____  ");
        getLogger().info(" | | | | ____|  / _ \\/ ___| ");
        getLogger().info(" | |_| |  _|   | | | \\___ \\ ");
        getLogger().info(" |  _  | |___  | |_| |___) |");
        getLogger().info(" |_| |_|_____|  \\___/|____/ ");
        getLogger().info("=================================");
        getLogger().info("Mod id: " + getDescription().getName().toLowerCase());
        getLogger().info("Mod version: " + getDescription().getVersion());
        getLogger().info("Mod author: " + MOD_AUTHOR);
        getLogger().info("Minecraft version: " + minecraftVersion());
    }

    private void applyConfigAliases() {
        CONFIG_ALIASES.forEach((legacyKey, nestedKey) -> {
            if (!getConfig().contains(legacyKey) && getConfig().contains(nestedKey)) {
                getConfig().set(legacyKey, getConfig().get(nestedKey));
            }
        });
    }

    @Override
    public void onDisable() {
        if (authService != null) {
            authService.close();
        }
        if (tpsDisplayService != null) {
            tpsDisplayService.close();
        }
        if (recipeSyncService != null) {
            recipeSyncService.close();
        }
        if (usernameValidationBypassService != null) {
            usernameValidationBypassService.close();
        }
    }

    private void registerCommands(FoliaBanCommands banCommands, FoliaMigrationCommands migrationCommands) {
        FoliaAuthCommands commands = new FoliaAuthCommands(authService);
        bind("login", commands);
        bind("register", commands);
        bind("changepassword", commands);

        bind("ban", banCommands);
        bind("ban-ip", banCommands);
        bind("unban", banCommands);
        bind("unban-ip", banCommands);
        bind("banlist", banCommands);

        bind("heos", new FoliaAdminCommands(this, storage, whitelistData, migrationCommands, authService, banCommands));
    }

    private void bind(String name, org.bukkit.command.CommandExecutor commands) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command is missing from plugin.yml: " + name);
            return;
        }
        command.setExecutor(commands);
        if (commands instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private boolean isRecipeViewerSyncEnabled() {
        return getConfig().getBoolean("enableRecipeViewerSync", false)
                && compareMinecraftVersions(minecraftVersion(), "1.21.2") >= 0;
    }

    private String minecraftVersion() {
        return getServer().getBukkitVersion().split("-", 2)[0];
    }

    private static int compareMinecraftVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int size = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < size; index++) {
            int leftPart = versionPart(leftParts, index);
            int rightPart = versionPart(rightParts, index);
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int versionPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
