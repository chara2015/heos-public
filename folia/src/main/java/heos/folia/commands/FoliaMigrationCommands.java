package heos.folia.commands;

import heos.folia.storage.FoliaBanData;
import heos.folia.storage.FoliaPlayerData;
import heos.folia.storage.FoliaStorage;
import heos.folia.utils.FoliaDisconnects;
import heos.folia.utils.FoliaMessages;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FoliaMigrationCommands {
    private static final long CONFIRM_TIMEOUT_MILLIS = 60_000L;
    private static final int MAX_MIGRATION_BAN_SECONDS = 30;
    private static final PlayerFileType[] PLAYER_FILE_TYPES = {
            new PlayerFileType("playerdata", ".dat"),
            new PlayerFileType("playerdata", ".dat_old"),
            new PlayerFileType("stats", ".json"),
            new PlayerFileType("advancements", ".json")
    };

    private final Plugin plugin;
    private final FoliaStorage storage;
    private final FoliaBanData banData;
    private final Map<UUID, PendingMigration> pendingMigrations = new ConcurrentHashMap<>();

    public FoliaMigrationCommands(Plugin plugin, FoliaStorage storage, FoliaBanData banData) {
        this.plugin = plugin;
        this.storage = storage;
        this.banData = banData;
    }

    public boolean onHeosSubcommand(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("migrate")) {
            return prepare(sender, args);
        }
        return false;
    }

    private boolean prepare(CommandSender sender, String[] args) {
        if (!plugin.getConfig().getBoolean("enablePlayerDataMigration", false)) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.migrationDisabled"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.migrationPlayerOnly"));
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageMigrate"));
            return true;
        }
        String source = args[1];
        String target = args[2];
        if (source.equalsIgnoreCase(target)) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.migrationSamePlayer"));
            return true;
        }

        String token = UUID.randomUUID().toString();
        pendingMigrations.put(player.getUniqueId(), new PendingMigration(source, target, token, System.currentTimeMillis()));
        sender.sendMessage(ChatColor.YELLOW + FoliaMessages.text(sender, "text.heos.migrationPrepared", source, target));
        sender.sendMessage(ChatColor.YELLOW + FoliaMessages.text(sender, "text.heos.migrationConfirmHint"));
        sendMigrationButtons(player, token);
        return true;
    }

    public void handleInternalAction(Player player, String action, String token) {
        if (!player.hasPermission("heos.admin")) {
            player.sendMessage(ChatColor.RED + FoliaMessages.text(player, "text.heos.noPermission"));
            return;
        }
        if (!plugin.getConfig().getBoolean("enablePlayerDataMigration", false)) {
            player.sendMessage(ChatColor.RED + FoliaMessages.text(player, "text.heos.migrationDisabled"));
            return;
        }
        if (!action.equals("confirm") && !action.equals("cancel")) {
            player.sendMessage(ChatColor.RED + FoliaMessages.text(player, "text.heos.migrationInvalidConfirmation"));
            return;
        }
        PendingMigration migration = pendingMigrations.get(player.getUniqueId());
        if (migration == null) {
            player.sendMessage(ChatColor.RED + FoliaMessages.text(player, "text.heos.migrationNoPending"));
            return;
        }
        if (!migration.token.equals(token)) {
            player.sendMessage(ChatColor.RED + FoliaMessages.text(player, "text.heos.migrationInvalidConfirmation"));
            return;
        }
        pendingMigrations.remove(player.getUniqueId());
        if (action.equals("confirm")) {
            if (System.currentTimeMillis() - migration.createdAt > CONFIRM_TIMEOUT_MILLIS) {
                player.sendMessage(ChatColor.RED + FoliaMessages.text(player, "text.heos.migrationExpired"));
                return;
            }
            execute(player, migration);
        } else if (action.equals("cancel")) {
            player.sendMessage(ChatColor.YELLOW + FoliaMessages.text(player, "text.heos.migrationCancelled"));
        }
    }

    private void execute(CommandSender sender, PendingMigration migration) {
        Player sourceOnline = Bukkit.getPlayerExact(migration.sourceUsername);
        Player targetOnline = Bukkit.getPlayerExact(migration.targetUsername);
        if (sourceOnline != null) {
            FoliaDisconnects.disconnect(sourceOnline, FoliaMessages.text(sourceOnline, "text.heos.migrationSourceDisconnect"), "HEOS_MIGRATION_SOURCE");
        }
        if (targetOnline != null) {
            FoliaDisconnects.disconnect(targetOnline, FoliaMessages.text(targetOnline, "text.heos.migrationTargetDisconnect"), "HEOS_MIGRATION_TARGET");
        }

        Set<UUID> sourceUuids = collectPlayerUuids(migration.sourceUsername);
        UUID sourceUuid = sourceUuids.iterator().next();
        UUID targetUuid = resolvePlayerUuid(migration.targetUsername);
        Path worldDir = primaryWorldPath();

        int copied = copyPlayerFiles(worldDir, sourceUuids, targetUuid);
        if (storage.exists(migration.sourceUsername)) {
            FoliaPlayerData sourceData = storage.load(migration.sourceUsername);
            FoliaPlayerData targetData = storage.load(migration.targetUsername);
            targetData.username = migration.targetUsername;
            targetData.uuid = targetUuid;
            targetData.passwordHash = sourceData.passwordHash;
            targetData.lastIp = sourceData.lastIp;
            targetData.isOnlineAccount = false;
            targetData.registeredTime = sourceData.registeredTime;
            targetData.lastLoginTime = System.currentTimeMillis();
            storage.save(targetData);
            copied++;
        }

        if (copied == 0) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.migrationNoData"));
            return;
        }

        int deleted = clearSourceData(worldDir, migration.sourceUsername, sourceUuids);
        int banSeconds = Math.min(MAX_MIGRATION_BAN_SECONDS, Math.max(1, plugin.getConfig().getInt("migrationBanSeconds", 30)));
        long banExpiry = System.currentTimeMillis() + banSeconds * 1000L;
        banData.addPlayerBan(migration.sourceUsername, sourceUuid, "Data migration in progress", banExpiry, sender.getName());

        sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.migrationComplete"));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.migrationSourcePlayer", migration.sourceUsername, sourceUuid));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.migrationTargetPlayer", migration.targetUsername, targetUuid));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.migrationEntries", copied));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.migrationCleanedEntries", deleted));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.migrationTemporaryBan", banSeconds));
    }

    private void sendMigrationButtons(Player player, String token) {
        TextComponent buttons = new TextComponent();
        buttons.addExtra(actionButton(player, "text.heos.migrationConfirmButton", net.md_5.bungee.api.ChatColor.GREEN, "/heos-internal-migration confirm " + token));
        buttons.addExtra(" ");
        buttons.addExtra(actionButton(player, "text.heos.migrationCancelButton", net.md_5.bungee.api.ChatColor.RED, "/heos-internal-migration cancel " + token));
        player.spigot().sendMessage(buttons);
    }

    private static TextComponent actionButton(Player player, String key, net.md_5.bungee.api.ChatColor color, String command) {
        TextComponent button = new TextComponent(FoliaMessages.text(player, key));
        button.setColor(color);
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        return button;
    }

    private Set<UUID> collectPlayerUuids(String username) {
        Set<UUID> uuids = new LinkedHashSet<>();
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            uuids.add(online.getUniqueId());
        }
        FoliaPlayerData data = storage.load(username);
        if (data.uuid != null) {
            uuids.add(data.uuid);
        }
        uuids.add(resolvePlayerUuid(username));
        return uuids;
    }

    private static UUID resolvePlayerUuid(String username) {
        @SuppressWarnings("deprecation")
        OfflinePlayer player = Bukkit.getOfflinePlayer(username);
        return player.getUniqueId();
    }

    private static Path primaryWorldPath() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return Path.of("world");
        }
        return world.getWorldFolder().toPath();
    }

    private static int copyPlayerFiles(Path worldDir, Set<UUID> sourceUuids, UUID targetUuid) {
        int copied = 0;
        for (PlayerFileType fileType : PLAYER_FILE_TYPES) {
            Path target = fileType.path(worldDir, targetUuid);
            for (UUID sourceUuid : sourceUuids) {
                if (copyIfExists(fileType.path(worldDir, sourceUuid), target)) {
                    copied++;
                    break;
                }
            }
        }
        return copied;
    }

    private int clearSourceData(Path worldDir, String sourceUsername, Set<UUID> sourceUuids) {
        int deleted = 0;
        for (UUID sourceUuid : sourceUuids) {
            for (PlayerFileType fileType : PLAYER_FILE_TYPES) {
                if (deleteIfExists(fileType.path(worldDir, sourceUuid))) {
                    deleted++;
                }
            }
        }
        if (storage.delete(sourceUsername)) {
            deleted++;
        }
        return deleted;
    }

    private static boolean copyIfExists(Path from, Path to) {
        if (!Files.exists(from)) {
            return false;
        }
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean deleteIfExists(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException ignored) {
            return false;
        }
    }

    private record PendingMigration(String sourceUsername, String targetUsername, String token, long createdAt) {
    }

    private record PlayerFileType(String directory, String suffix) {
        Path path(Path worldDir, UUID uuid) {
            return worldDir.resolve(directory).resolve(uuid + suffix);
        }
    }
}
