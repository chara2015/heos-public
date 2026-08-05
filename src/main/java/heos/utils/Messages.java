package heos.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import heos.Heos;
import heos.interfaces.PlayerAuth;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Localized messages for the server locale or a player's Minecraft locale.
 */
public final class Messages {
    private static final Gson GSON = new Gson();
    private static final Map<String, String> FALLBACK = loadLanguage("en_us");
    private static final Map<String, Map<String, String>> LANGUAGES = new ConcurrentHashMap<>();

    private Messages() {
    }

    private static Map<String, String> loadLanguage(String language) {
        try {
            var stream = Messages.class.getResourceAsStream("/data/heos/lang/" + language.toLowerCase(Locale.ENGLISH) + ".json");
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

    private static String translate(String key) {
        return translate(key, serverLanguage());
    }

    private static String translate(ServerPlayer player, String key) {
        return translate(key, playerLanguage(player));
    }

    private static String translate(String key, String language) {
        Map<String, String> current = LANGUAGES.computeIfAbsent(resourceLanguage(language), Messages::loadLanguage);
        if (current.containsKey(key)) {
            return current.get(key);
        }
        return FALLBACK.getOrDefault(key, key);
    }

    public static String text(String key, Object... args) {
        return translate(key).formatted(args);
    }

    public static String text(ServerPlayer player, String key, Object... args) {
        return translate(player, key).formatted(args);
    }

    public static String text(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = source == null ? null : source.getPlayer();
        return player == null ? text(key, args) : text(player, key, args);
    }

    private static String serverLanguage() {
        String configured = Heos.getConfig() == null ? "auto" : Heos.getConfig().language;
        return configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured)
                ? Locale.getDefault().toString()
                : configured;
    }

    private static String playerLanguage(ServerPlayer player) {
        if (player == null) {
            return serverLanguage();
        }
        String language = ((PlayerAuth) player).heos$getClientLanguage();
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

    public static String authPromptLogin(ServerPlayer player) {
        return translate(player, "text.heos.loginInputHint");
    }

    public static String authPromptRegister() {
        return translate("text.heos.registerInputHint");
    }

    public static String authPromptRegister(ServerPlayer player) {
        return translate(player, "text.heos.registerInputHint");
    }

    public static String offlineNameHint() {
        return translate("text.heos.disallowedUsername").formatted(allowedUsernamePattern());
    }

    public static String offlineNameLogOnly() {
        return "HEOS_OFFLINE_NAME_RULE";
    }

    public static String invalidOfflineNameLog() {
        return translate("text.heos.disallowedUsername").formatted(allowedUsernamePattern());
    }

    public static String loginTimeout() {
        return translate("text.heos.timeExpired");
    }

    public static String loginTimeout(ServerPlayer player) {
        return translate(player, "text.heos.timeExpired");
    }

    public static String premiumWelcome() {
        return translate("text.heos.onlinePlayerLogin");
    }

    public static String premiumWelcome(ServerPlayer player) {
        return translate(player, "text.heos.onlinePlayerLogin");
    }

    public static String authServiceUnavailable() {
        return translate("text.heos.authServiceUnavailable");
    }

    public static String loginInputHint() {
        return translate("text.heos.loginInputHint");
    }

    public static String loginInputHint(ServerPlayer player) {
        return translate(player, "text.heos.loginInputHint");
    }

    public static String registerInputHint() {
        return translate("text.heos.registerRequired");
    }

    public static String registerInputHint(ServerPlayer player) {
        return translate(player, "text.heos.registerRequired");
    }

    public static String alreadyLoggedIn() {
        return translate("text.heos.alreadyAuthenticated");
    }

    public static String alreadyLoggedIn(ServerPlayer player) {
        return translate(player, "text.heos.alreadyAuthenticated");
    }

    public static String premiumNoLogin() {
        return translate("text.heos.onlinePlayerLogin");
    }

    public static String premiumNoLogin(ServerPlayer player) {
        return translate(player, "text.heos.onlinePlayerLogin");
    }

    public static String premiumNoRegister() {
        return translate("text.heos.onlinePlayerLogin");
    }

    public static String premiumNoRegister(ServerPlayer player) {
        return translate(player, "text.heos.onlinePlayerLogin");
    }

    public static String notRegistered() {
        return translate("text.heos.userNotRegistered");
    }

    public static String notRegistered(ServerPlayer player) {
        return translate(player, "text.heos.userNotRegistered");
    }

    public static String alreadyRegistered() {
        return translate("text.heos.alreadyRegistered");
    }

    public static String alreadyRegistered(ServerPlayer player) {
        return translate(player, "text.heos.alreadyRegistered");
    }

    public static String loginSuccess() {
        return translate("text.heos.successfullyAuthenticated");
    }

    public static String loginSuccess(ServerPlayer player) {
        return translate(player, "text.heos.successfullyAuthenticated");
    }

    public static String wrongPassword() {
        return translate("text.heos.wrongPassword");
    }

    public static String wrongPassword(ServerPlayer player) {
        return translate(player, "text.heos.wrongPassword");
    }

    public static String passwordTooShort() {
        return translate("text.heos.minPasswordChars");
    }

    public static String passwordTooShort(ServerPlayer player) {
        return translate(player, "text.heos.minPasswordChars");
    }

    public static String passwordTooLong() {
        return translate("text.heos.maxPasswordChars");
    }

    public static String passwordTooLong(ServerPlayer player) {
        return translate(player, "text.heos.maxPasswordChars");
    }

    public static String passwordMismatch() {
        return translate("text.heos.matchPassword");
    }

    public static String passwordMismatch(ServerPlayer player) {
        return translate(player, "text.heos.matchPassword");
    }

    public static String registerFailed() {
        return translate("text.heos.registerRequired");
    }

    public static String registerFailed(ServerPlayer player) {
        return translate(player, "text.heos.registerRequired");
    }

    public static String registerSuccess() {
        return translate("text.heos.registerSuccess");
    }

    public static String registerSuccess(ServerPlayer player) {
        return translate(player, "text.heos.registerSuccess");
    }

    public static String keepPasswordSafe() {
        return translate("text.heos.keepPasswordSafe");
    }

    public static String keepPasswordSafe(ServerPlayer player) {
        return translate(player, "text.heos.keepPasswordSafe");
    }

    public static String changePasswordLoginRequired(ServerPlayer player) {
        return translate(player, "text.heos.changePasswordLoginRequired");
    }

    public static String premiumNoPasswordChange(ServerPlayer player) {
        return translate(player, "text.heos.premiumNoPasswordChange");
    }

    public static String changePasswordNotRegistered(ServerPlayer player) {
        return translate(player, "text.heos.changePasswordNotRegistered");
    }

    public static String oldPasswordIncorrect(ServerPlayer player) {
        return translate(player, "text.heos.oldPasswordIncorrect");
    }

    public static String newPasswordTooShort(ServerPlayer player, int min) {
        return translate(player, "text.heos.newPasswordTooShort").formatted(min);
    }

    public static String newPasswordTooLong(ServerPlayer player, int max) {
        return translate(player, "text.heos.newPasswordTooLong").formatted(max);
    }

    public static String newPasswordSameAsOld(ServerPlayer player) {
        return translate(player, "text.heos.newPasswordSameAsOld");
    }

    public static String changePasswordFailed(ServerPlayer player) {
        return translate(player, "text.heos.changePasswordFailed");
    }

    public static String changePasswordSuccess(ServerPlayer player) {
        return translate(player, "text.heos.changePasswordSuccess");
    }

    public static boolean isMigrationReason(String reason) {
        if (reason == null) {
            return false;
        }
        String normalized = reason.toLowerCase();
        return normalized.contains("data migration in progress")
                || normalized.contains("migration in progress");
    }

    public static String migrationBanLogOnly() {
        return "HEOS_MIGRATION_BAN";
    }

    public static String loginFailureLock(long seconds) {
        return translate("text.heos.loginFailureLock").formatted(seconds);
    }

    public static String loginFailureLock(ServerPlayer player, long seconds) {
        return translate(player, "text.heos.loginFailureLock").formatted(seconds);
    }

    public static String whitelistLogOnly() {
        return "HEOS_WHITELIST";
    }

    public static String whitelistKick() {
        return translate("text.heos.whitelistKick");
    }

    public static String whitelistDeniedLog(String username) {
        return translate("text.heos.whitelistDeniedLog").formatted(username);
    }

    public static String banMessage(String reason, String expiry) {
        return translate("text.heos.banMessage").formatted(reason, expiry);
    }

    public static String banMessage(ServerPlayer player, String reason, String expiry) {
        return translate(player, "text.heos.banMessage").formatted(reason, expiry);
    }

    public static String banIpMessage(String reason, String expiry) {
        return translate("text.heos.banIpMessage").formatted(reason, expiry);
    }

    public static String banIpMessage(ServerPlayer player, String reason, String expiry) {
        return translate(player, "text.heos.banIpMessage").formatted(reason, expiry);
    }

    public static String migrationBanAttemptLog(String username) {
        return translate("text.heos.playerAlreadyOnline").formatted(username);
    }

    public static String updateSuppressionCrash(String detail) {
        return translate("text.heos.updateSuppressionCrash").formatted(detail);
    }

    public static String unknownPosition() {
        return translate("text.heos.unknownPosition");
    }

    private static String allowedUsernamePattern() {
        if (Heos.getConfig() != null
                && Heos.getConfig().allowMoreOfflineUsernameCharacters
                && Heos.getConfig().allowUnicodeOfflineUsernameCharacters) {
            return translate("text.heos.usernamePatternExtended");
        }
        if (Heos.getConfig() != null && Heos.getConfig().allowUnicodeOfflineUsernameCharacters) {
            return translate("text.heos.usernamePatternUnicode");
        }
        if (Heos.getConfig() != null && Heos.getConfig().allowMoreOfflineUsernameCharacters) {
            return translate("text.heos.usernamePatternAdditional");
        }
        return translate("text.heos.usernamePatternSimple");
    }
}
