package heos.folia.commands;

import heos.folia.utils.FoliaPasswordHasher;
import heos.folia.storage.FoliaPlayerData;
import heos.folia.storage.FoliaStorage;
import heos.folia.storage.FoliaWhitelistData;
import heos.folia.event.FoliaAuthService;
import heos.folia.utils.FoliaMessages;
import heos.folia.utils.FoliaTimeParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FoliaAdminCommands implements CommandExecutor, TabCompleter {
    private final FoliaStorage storage;
    private final FoliaWhitelistData whitelistData;
    private final FoliaMigrationCommands migrationCommands;
    private final org.bukkit.plugin.Plugin plugin;
    private final FoliaAuthService authService;
    private final FoliaBanCommands banCommands;

    public FoliaAdminCommands(org.bukkit.plugin.Plugin plugin, FoliaStorage storage, FoliaWhitelistData whitelistData, FoliaMigrationCommands migrationCommands, FoliaAuthService authService, FoliaBanCommands banCommands) {
        this.plugin = plugin;
        this.storage = storage;
        this.whitelistData = whitelistData;
        this.migrationCommands = migrationCommands;
        this.authService = authService;
        this.banCommands = banCommands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        String sub = args[0].toLowerCase();

        // Always-available auth subcommands: avoids needing `heos:` prefix when /login or /register conflicts.
        if (sub.equals("login") || sub.equals("register") || sub.equals("changepassword")) {
            return auth(sender, args);
        }

        // Admin-only subcommands
        if (!sender.hasPermission("heos.admin")) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.noPermission"));
            return true;
        }

        return switch (sub) {
            case "resetpassword" -> resetPassword(sender, args);
            case "info" -> info(sender, args);
            case "whitelist" -> whitelist(sender, args);
            case "migrate" -> migrationCommands.onHeosSubcommand(sender, args);
            case "reload" -> reload(sender, args);
            case "ban", "ban-ip", "unban", "unban-ip", "banlist" -> banCommands.onSubcommand(sender, sub, shiftArgs(args));
            default -> false;
        };
    }

    private boolean auth(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.playerOnlyCommand"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("login")) {
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageHeosLogin"));
                return true;
            }
            authService.login(player, args[1]);
            return true;
        }
        if (sub.equals("register")) {
            if (args.length != 3) {
                sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageHeosRegister"));
                return true;
            }
            authService.register(player, args[1], args[2]);
            return true;
        }
        if (sub.equals("changepassword")) {
            if (args.length != 3) {
                sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageHeosChangePassword"));
                return true;
            }
            authService.changePassword(player, args[1], args[2]);
            return true;
        }
        return false;
    }

    private static String[] shiftArgs(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] shifted = new String[args.length - 1];
        System.arraycopy(args, 1, shifted, 0, shifted.length);
        return shifted;
    }

    private boolean resetPassword(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageResetPassword"));
            return true;
        }
        String username = args[1];
        String password = args[2];
        FoliaPlayerData data = storage.load(username);
        if (!data.isRegistered()) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.adminPlayerNotRegistered", username));
            return true;
        }
        data.passwordHash = FoliaPasswordHasher.hashPassword(password);
        storage.save(data);
        sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.adminResetPasswordSuccess", username));

        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            online.sendMessage(ChatColor.YELLOW + FoliaMessages.text(online, "text.heos.passwordResetNotice"));
            online.sendMessage(ChatColor.YELLOW + FoliaMessages.text(online, "text.heos.newPasswordNotice", password));
            online.sendMessage(ChatColor.YELLOW + FoliaMessages.text(online, "text.heos.changePasswordSoon"));
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usagePlayerInfo"));
            return true;
        }
        FoliaPlayerData data = storage.loadStored(args[1]);
        if (data == null) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.playerNoStoredData", args[1]));
            return true;
        }
        String unknown = FoliaMessages.text(sender, "text.heos.unknownValue");
        sender.sendMessage(ChatColor.YELLOW + FoliaMessages.text(sender, "text.heos.playerInfoTitle", data.username));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.playerInfoUuid", data.uuid == null ? unknown : data.uuid));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.playerInfoLastIp", data.lastIp == null || data.lastIp.isBlank() ? unknown : data.lastIp));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.playerInfoRegisteredAt", data.registeredTime > 0L ? FoliaTimeParser.formatDateTime(sender, data.registeredTime) : unknown));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.playerInfoLastLogin", data.lastLoginTime > 0L ? FoliaTimeParser.formatDateTime(sender, data.lastLoginTime) : unknown));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.playerInfoAccountType", FoliaMessages.text(sender, data.isOnlineAccount ? "text.heos.accountPremium" : "text.heos.accountOffline")));
        return true;
    }

    private boolean whitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageWhitelist"));
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageWhitelistAdd"));
                    return true;
                }
                if (whitelistData.add(args[2])) {
                    sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.whitelistAdded", args[2]));
                } else {
                    sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.whitelistAlreadyAdded", args[2]));
                }
                return true;
            }
            case "remove" -> {
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageWhitelistRemove"));
                    return true;
                }
                if (whitelistData.remove(args[2])) {
                    sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.whitelistRemoved", args[2]));
                } else {
                    sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.whitelistNotAdded", args[2]));
                }
                return true;
            }
            case "list" -> {
                sender.sendMessage(ChatColor.YELLOW + FoliaMessages.text(sender, "text.heos.whitelistSize", whitelistData.usernames.size()));
                if (!whitelistData.usernames.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + String.join(", ", whitelistData.usernames));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("heos.admin")) {
                return filter(List.of("login", "register", "changepassword", "ban", "ban-ip", "unban", "unban-ip", "banlist", "resetpassword", "info", "whitelist", "migrate", "reload"), args[0]);
            }
            return filter(List.of("login", "register", "changepassword"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            if (!sender.hasPermission("heos.admin")) {
                return Collections.emptyList();
            }
            return filter(List.of("add", "remove", "list"), args[1]);
        }
        if ((args.length == 2 && (args[0].equalsIgnoreCase("resetpassword") || args[0].equalsIgnoreCase("info")))
                || (args.length >= 2 && args.length <= 3 && args[0].equalsIgnoreCase("migrate"))) {
            if (!sender.hasPermission("heos.admin")) {
                return Collections.emptyList();
            }
            return filterPlayerNames(knownPlayerNames(), args[args.length - 1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist") && args[1].equalsIgnoreCase("remove")) {
            if (!sender.hasPermission("heos.admin")) {
                return Collections.emptyList();
            }
            return filterPlayerNames(whitelistData.usernames, args[2]);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban")
                || args[0].equalsIgnoreCase("ban-ip") || args[0].equalsIgnoreCase("unban-ip"))) {
            return banCommands.tabComplete(sender, args[0], shiftArgs(args));
        }
        return Collections.emptyList();
    }

    private boolean reload(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageReload"));
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.configReloaded"));
        return true;
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private Set<String> knownPlayerNames() {
        Set<String> names = new LinkedHashSet<>(storage.usernames());
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> filterPlayerNames(Set<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
