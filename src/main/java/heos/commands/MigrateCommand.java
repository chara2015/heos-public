package heos.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import heos.Heos;
import heos.integrations.Permissions;
import heos.storage.PlayerData;
import heos.utils.HeosLogger;
import heos.utils.Messages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Player data migration helpers.
 */
public final class MigrateCommand {
    private static final int MAX_MIGRATION_BAN_SECONDS = 30;
    private static final long CONFIRM_TIMEOUT_MILLIS = 60_000L;
    private static final Map<String, PendingMigration> PENDING_MIGRATIONS = new ConcurrentHashMap<>();
    private static final PlayerFileType[] PLAYER_FILE_TYPES = {
            new PlayerFileType("playerdata", ".dat"),
            new PlayerFileType("playerdata", ".dat_old"),
            new PlayerFileType("stats", ".json"),
            new PlayerFileType("advancements", ".json")
    };

    private MigrateCommand() {
    }

    public static int prepareMigrate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!isMigrationEnabled(source)) {
            return 0;
        }

        String sourceUsername = StringArgumentType.getString(context, "sourcePlayer");
        String targetUsername = StringArgumentType.getString(context, "targetPlayer");

        if (sourceUsername.equalsIgnoreCase(targetUsername)) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationSamePlayer")));
            return 0;
        }

        ServerPlayer confirmer = source.getPlayer();
        if (confirmer == null) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationPlayerOnly")));
            return 0;
        }

        String token = UUID.randomUUID().toString();
        PENDING_MIGRATIONS.put(sourceKey(source), new PendingMigration(sourceUsername, targetUsername, token, System.currentTimeMillis()));
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationPrepared", sourceUsername, targetUsername)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationConfirmHint")), false);
        source.sendSuccess(() -> migrationButtons(source, token), false);
        return 1;
    }

    public static void handleInternalAction(ServerPlayer player, String action, String token) {
        CommandSourceStack source = player.createCommandSourceStack();
        if (!Permissions.requireLevel(3).test(source)) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.noPermission")));
            return;
        }
        if (!isMigrationEnabled(source)) {
            return;
        }
        if (!"confirm".equals(action) && !"cancel".equals(action)) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationInvalidConfirmation")));
            return;
        }

        String key = sourceKey(source);
        PendingMigration migration = PENDING_MIGRATIONS.get(key);
        if (migration == null) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationNoPending")));
            return;
        }
        if (!migration.token.equals(token)) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationInvalidConfirmation")));
            return;
        }
        PENDING_MIGRATIONS.remove(key);
        if ("confirm".equals(action)) {
            if (System.currentTimeMillis() - migration.createdAt > CONFIRM_TIMEOUT_MILLIS) {
                source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationExpired")));
                return;
            }
            executeMigrate(source, migration.sourceUsername, migration.targetUsername);
        } else if ("cancel".equals(action)) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationCancelled")), false);
        }
    }

    private static boolean isMigrationEnabled(CommandSourceStack source) {
        if (Heos.getConfig().enablePlayerDataMigration) {
            return true;
        }
        source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationDisabled")));
        return false;
    }

    private static Component migrationButtons(CommandSourceStack source, String token) {
        return Component.empty()
                .append(actionButton(source, "text.heos.migrationConfirmButton", ChatFormatting.GREEN, "/heos-internal-migration confirm " + token))
                .append(Component.literal(" "))
                .append(actionButton(source, "text.heos.migrationCancelButton", ChatFormatting.RED, "/heos-internal-migration cancel " + token));
    }

    private static Component actionButton(CommandSourceStack source, String key, ChatFormatting color, String command) {
        return Component.literal(Messages.text(source, key))
                .withStyle(style -> style
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(runCommand(command)));
    }

    private static ClickEvent runCommand(String command) {
        //? if >= 1.21.5 {
        return new ClickEvent.RunCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
    }

    private static int executeMigrate(CommandSourceStack source, String sourceUsername, String targetUsername) {
        MinecraftServer server = source.getServer();
        ServerPlayer sourceOnline = server.getPlayerList().getPlayerByName(sourceUsername);
        ServerPlayer targetOnline = server.getPlayerList().getPlayerByName(targetUsername);

        if (sourceOnline != null) {
            sourceOnline.connection.disconnect(Component.literal(Messages.text(sourceOnline, "text.heos.migrationSourceDisconnect")));
        }
        if (targetOnline != null) {
            targetOnline.connection.disconnect(Component.literal(Messages.text(targetOnline, "text.heos.migrationTargetDisconnect")));
        }

        Set<UUID> sourceUuids = collectPlayerUuids(server, sourceUsername);
        UUID sourceUuid = sourceUuids.iterator().next();
        UUID targetUuid = resolvePlayerUuid(server, targetUsername);
        if (targetUuid == null) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationUuidFailed")));
            return 0;
        }

        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        int filesCopied = 0;

        filesCopied += copyPlayerFiles(worldDir, sourceUuids, targetUuid);

        if (PlayerData.exists(sourceUsername)) {
            PlayerData sourceData = Heos.getPlayerData(sourceUsername);
            PlayerData targetData = Heos.getPlayerData(targetUsername);
            targetData.username = targetUsername;
            targetData.uuid = targetUuid;
            targetData.passwordHash = sourceData.passwordHash;
            targetData.lastIp = sourceData.lastIp;
            targetData.isOnlineAccount = isCurrentOnlineSessionPremium(targetOnline, targetUsername);
            targetData.registeredTime = sourceData.registeredTime;
            targetData.lastLoginTime = System.currentTimeMillis();
            targetData.save();
            filesCopied++;
        }

        if (filesCopied == 0) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.migrationNoData")));
            return 0;
        }

        int filesDeleted = clearSourceData(worldDir, sourceUsername, sourceUuids);
        Heos.invalidatePlayerData(sourceUsername);
        Heos.invalidatePlayerData(targetUsername);

        String banReason = "Data migration in progress";
        int migrationBanSeconds = Math.min(MAX_MIGRATION_BAN_SECONDS, Math.max(1, Heos.getConfig().migrationBanSeconds));
        long banExpiry = System.currentTimeMillis() + migrationBanSeconds * 1000L;
        BanCommands.banPlayerProgrammatic(sourceUsername, sourceUuid, banReason, banExpiry, source.getTextName());

        HeosLogger.info("Admin " + source.getTextName() + " migrated all available data from "
                + sourceUsername + " to " + targetUsername + ", copied " + filesCopied + " entries/files, deleted " + filesDeleted + " source entries/files");

        final int copied = filesCopied;
        final int deleted = filesDeleted;
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationComplete")), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationSourcePlayer", sourceUsername, sourceUuid)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationTargetPlayer", targetUsername, targetUuid)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationEntries", copied)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationCleanedEntries", deleted)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.migrationTemporaryBan", migrationBanSeconds)), false);
        return 1;
    }

    private static UUID resolvePlayerUuid(MinecraftServer server, String username) {
        return collectPlayerUuids(server, username).iterator().next();
    }

    private static Set<UUID> collectPlayerUuids(MinecraftServer server, String username) {
        Set<UUID> uuids = new LinkedHashSet<>();
        ServerPlayer online = server.getPlayerList().getPlayerByName(username);
        if (online != null) {
            uuids.add(online.getUUID());
        }
        PlayerData data = Heos.getPlayerData(username);
        if (data.uuid != null) {
            uuids.add(data.uuid);
        }
        uuids.add(UUIDUtil.createOfflinePlayerUUID(username));
        return uuids;
    }

    private static boolean isCurrentOnlineSessionPremium(ServerPlayer player, String username) {
        return player != null
                && player.level().getServer().usesAuthentication()
                && !player.getUUID().equals(UUIDUtil.createOfflinePlayerUUID(username));
    }

    private static int copyIfExists(Path from, Path to) {
        if (!Files.exists(from)) {
            return 0;
        }
        try {
            Path parent = to.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            HeosLogger.debug("Copied " + from + " -> " + to);
            return 1;
        } catch (IOException e) {
            HeosLogger.error("Failed to copy " + from + " -> " + to, e);
            return 0;
        }
    }

    private static int copyPlayerFiles(Path worldDir, Set<UUID> sourceUuids, UUID targetUuid) {
        int copied = 0;
        for (PlayerFileType fileType : PLAYER_FILE_TYPES) {
            Path target = fileType.path(worldDir, targetUuid);
            for (UUID sourceUuid : sourceUuids) {
                if (copyIfExists(fileType.path(worldDir, sourceUuid), target) > 0) {
                    copied++;
                    break;
                }
            }
        }
        return copied;
    }

    private static int clearSourceData(Path worldDir, String sourceUsername, Set<UUID> sourceUuids) {
        int filesDeleted = 0;
        for (UUID sourceUuid : sourceUuids) {
            for (PlayerFileType fileType : PLAYER_FILE_TYPES) {
                filesDeleted += deleteIfExists(fileType.path(worldDir, sourceUuid));
            }
        }
        if (PlayerData.delete(sourceUsername)) {
            filesDeleted++;
        }
        return filesDeleted;
    }

    private static int deleteIfExists(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                HeosLogger.debug("Deleted migrated source file " + path);
                return 1;
            }
        } catch (IOException e) {
            HeosLogger.error("Failed to delete migrated source file " + path, e);
        }
        return 0;
    }

    private static String sourceKey(CommandSourceStack source) {
        try {
            if (source.isPlayer()) {
                return source.getPlayerOrException().getUUID().toString();
            }
        } catch (Exception ignored) {
        }
        return "console:" + source.getTextName();
    }

    private record PendingMigration(String sourceUsername, String targetUsername, String token, long createdAt) {
    }

    private record PlayerFileType(String directory, String suffix) {
        Path path(Path worldDir, UUID uuid) {
            return worldDir.resolve(directory + "/" + uuid + suffix);
        }
    }
}
