package heos.folia.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.CommandSender;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FoliaMessages {
    private static final String DEFAULT_LANGUAGE = "auto";
    private static final Gson GSON = new Gson();
    private static Map<String, String> FALLBACK = Collections.emptyMap();
    private static final Map<String, Map<String, String>> LANGUAGES = new ConcurrentHashMap<>();
    private static Plugin plugin;

    private FoliaMessages() {
    }

    public static void init(Plugin instance) {
        plugin = instance;
        FALLBACK = loadLanguage("en_us");
    }

    private static Map<String, String> loadLanguage(String language) {
        try {
            var stream = FoliaMessages.class.getResourceAsStream("/data/heos/lang/" + language.toLowerCase(Locale.ENGLISH) + ".json");
            if (stream == null) {
                return Collections.emptyMap();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, String> map = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                return map == null ? Collections.emptyMap() : map;
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public static String translate(String key) {
        return translate(key, serverLanguage());
    }

    public static String translate(Player player, String key) {
        return translate(key, playerLanguage(player));
    }

    private static String translate(String key, String language) {
        Map<String, String> current = LANGUAGES.computeIfAbsent(resourceLanguage(language), FoliaMessages::loadLanguage);
        if (current.containsKey(key)) {
            return current.get(key);
        }
        return FALLBACK.getOrDefault(key, key);
    }

    public static String text(CommandSender sender, String key, Object... args) {
        String message = sender instanceof Player player ? translate(player, key) : translate(key);
        return message.formatted(args);
    }

    public static String text(Player player, String key, Object... args) {
        return translate(player, key).formatted(args);
    }

    private static String serverLanguage() {
        String configured = plugin == null ? DEFAULT_LANGUAGE : plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        return configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured)
                ? Locale.getDefault().toString()
                : configured;
    }

    private static String playerLanguage(Player player) {
        if (player == null) {
            return serverLanguage();
        }
        String language = player.getLocale();
        return language == null || language.isBlank() ? serverLanguage() : language;
    }

    private static String resourceLanguage(String language) {
        String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT).replace('-', '_');
        if (!loadLanguage(normalized).isEmpty()) {
            return normalized;
        }
        if (normalized.startsWith("zh")) {
            return "zh_cn";
        }
        return "en_us";
    }

    public static String authPromptLogin() {
        return translate("text.heos.loginInputHint");
    }

    public static String authPromptLogin(Player player) {
        return translate(player, "text.heos.loginInputHint");
    }

    public static String authPromptRegister() {
        return translate("text.heos.registerInputHint");
    }

    public static String authPromptRegister(Player player) {
        return translate(player, "text.heos.registerInputHint");
    }

    public static String offlineNameHint() {
        return translate("text.heos.disallowedUsername").formatted(allowedUsernamePattern());
    }

    public static String invalidOfflineNameLog() {
        return translate("text.heos.disallowedUsername").formatted(allowedUsernamePattern());
    }

    public static String loginTimeout() {
        return translate("text.heos.timeExpired");
    }

    public static String loginTimeout(Player player) {
        return translate(player, "text.heos.timeExpired");
    }

    public static String premiumWelcome() {
        return translate("text.heos.onlinePlayerLogin");
    }

    public static String premiumWelcome(Player player) {
        return translate(player, "text.heos.onlinePlayerLogin");
    }

    public static String authServiceUnavailable() {
        return translate("text.heos.authServiceUnavailable");
    }

    public static String loginInputHint() {
        return translate("text.heos.loginInputHint");
    }

    public static String loginInputHint(Player player) {
        return translate(player, "text.heos.loginInputHint");
    }

    public static String registerInputHint() {
        return translate("text.heos.registerRequired");
    }

    public static String registerInputHint(Player player) {
        return translate(player, "text.heos.registerRequired");
    }

    public static String alreadyLoggedIn() {
        return translate("text.heos.alreadyAuthenticated");
    }

    public static String premiumNoLogin() {
        return translate("text.heos.onlinePlayerLogin");
    }

    public static String premiumNoRegister() {
        return translate("text.heos.onlinePlayerLogin");
    }

    public static String notRegistered() {
        return translate("text.heos.userNotRegistered");
    }

    public static String notRegistered(Player player) {
        return translate(player, "text.heos.userNotRegistered");
    }

    public static String alreadyRegistered() {
        return translate("text.heos.alreadyRegistered");
    }

    public static String alreadyRegistered(Player player) {
        return translate(player, "text.heos.alreadyRegistered");
    }

    public static String loginSuccess() {
        return translate("text.heos.successfullyAuthenticated");
    }

    public static String loginSuccess(Player player) {
        return translate(player, "text.heos.successfullyAuthenticated");
    }

    public static String wrongPassword() {
        return translate("text.heos.wrongPassword");
    }

    public static String wrongPassword(Player player) {
        return translate(player, "text.heos.wrongPassword");
    }

    public static String passwordTooShort() {
        return translate("text.heos.minPasswordChars");
    }

    public static String passwordTooShort(Player player) {
        return translate(player, "text.heos.minPasswordChars");
    }

    public static String passwordTooLong() {
        return translate("text.heos.maxPasswordChars");
    }

    public static String passwordTooLong(Player player) {
        return translate(player, "text.heos.maxPasswordChars");
    }

    public static String passwordMismatch() {
        return translate("text.heos.matchPassword");
    }

    public static String passwordMismatch(Player player) {
        return translate(player, "text.heos.matchPassword");
    }

    public static String registerFailed() {
        return translate("text.heos.registerRequired");
    }

    public static String registerSuccess() {
        return translate("text.heos.registerSuccess");
    }

    public static String registerSuccess(Player player) {
        return translate(player, "text.heos.registerSuccess");
    }

    public static String keepPasswordSafe() {
        return translate("text.heos.keepPasswordSafe");
    }

    public static String keepPasswordSafe(Player player) {
        return translate(player, "text.heos.keepPasswordSafe");
    }

    public static String newPasswordSameAsOld(Player player) {
        return translate(player, "text.heos.newPasswordSameAsOld");
    }

    public static boolean isMigrationReason(String reason) {
        if (reason == null) {
            return false;
        }
        String normalized = reason.toLowerCase();
        return normalized.contains("data migration in progress")
                || normalized.contains("migration in progress");
    }

    public static String whitelistKick() {
        return translate("text.heos.whitelistKick");
    }

    public static String loginFailureLock(long seconds) {
        return translate("text.heos.loginFailureLock").formatted(seconds);
    }

    public static String loginFailureLock(Player player, long seconds) {
        return translate(player, "text.heos.loginFailureLock").formatted(seconds);
    }

    public static String whitelistDeniedLog(String username) {
        return translate("text.heos.whitelistDeniedLog").formatted(username);
    }

    public static String banMessage(String reason, String expiry) {
        return translate("text.heos.banMessage").formatted(reason, expiry);
    }

    public static String banMessage(Player player, String reason, String expiry) {
        return translate(player, "text.heos.banMessage").formatted(reason, expiry);
    }

    public static String banIpMessage(String reason, String expiry) {
        return translate("text.heos.banIpMessage").formatted(reason, expiry);
    }

    public static String banIpMessage(Player player, String reason, String expiry) {
        return translate(player, "text.heos.banIpMessage").formatted(reason, expiry);
    }

    public static String migrationBanAttemptLog(String username) {
        return translate("text.heos.playerAlreadyOnline").formatted(username);
    }

    private static String allowedUsernamePattern() {
        if (plugin != null
                && plugin.getConfig().getBoolean("allowMoreOfflineUsernameCharacters", false)
                && plugin.getConfig().getBoolean("allowUnicodeOfflineUsernameCharacters", false)) {
            return translate("text.heos.usernamePatternExtended");
        }
        if (plugin != null && plugin.getConfig().getBoolean("allowUnicodeOfflineUsernameCharacters", false)) {
            return translate("text.heos.usernamePatternUnicode");
        }
        if (plugin != null && plugin.getConfig().getBoolean("allowMoreOfflineUsernameCharacters", false)) {
            return translate("text.heos.usernamePatternAdditional");
        }
        return translate("text.heos.usernamePatternSimple");
    }
}
