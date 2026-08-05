package heos.folia.commands;

import heos.folia.storage.FoliaBanData;
import heos.folia.storage.FoliaStorage;
import heos.folia.utils.FoliaDisconnects;
import heos.folia.utils.FoliaMessages;
import heos.folia.utils.FoliaPlayerAccess;
import heos.folia.utils.FoliaTimeParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class FoliaBanCommands implements CommandExecutor, TabCompleter {
    private final FoliaBanData banData;
    private final FoliaStorage storage;

    public FoliaBanCommands(FoliaBanData banData, FoliaStorage storage) {
        this.banData = banData;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("heos.admin")) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.noPermission"));
            return true;
        }

        return onSubcommand(sender, command.getName().toLowerCase(), args);
    }

    public boolean onSubcommand(CommandSender sender, String subcommand, String[] args) {
        if (!sender.hasPermission("heos.admin")) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.noPermission"));
            return true;
        }
        return switch (subcommand) {
            case "ban" -> banPlayer(sender, args);
            case "ban-ip" -> banIp(sender, args);
            case "unban" -> unban(sender, args);
            case "unban-ip" -> unbanIp(sender, args);
            case "banlist" -> list(sender);
            default -> false;
        };
    }

    private boolean banPlayer(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return false;
        }
        ParsedBan parsed = parse(sender, args, 1);
        if (parsed.expiryTime == -2L) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.invalidBanTime"));
            return true;
        }

        String username = args[0];
        Player target = Bukkit.getPlayerExact(username);
        UUID uuid = target == null ? null : target.getUniqueId();
        banData.addPlayerBan(username, uuid, parsed.reason, parsed.expiryTime, sender.getName());

        sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.bannedPlayer", username));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.banReason", parsed.reason));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.banDuration", FoliaTimeParser.formatDuration(sender, parsed.expiryTime)));

        if (target != null) {
            FoliaDisconnects.disconnect(target, banMessage(target, parsed.reason, parsed.expiryTime), "HEOS_BAN");
        }
        return true;
    }

    private boolean banIp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return false;
        }
        ParsedBan parsed = parse(sender, args, 1);
        if (parsed.expiryTime == -2L) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.invalidBanTime"));
            return true;
        }

        String target = args[0];
        String ip = resolveIp(target);
        if (ip == null) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.playerIpNotFound", target));
            return true;
        }
        banData.addIpBan(ip, parsed.reason, parsed.expiryTime, sender.getName());
        sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.bannedIp", ip));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.banReason", parsed.reason));
        sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.banDuration", FoliaTimeParser.formatDuration(sender, parsed.expiryTime)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ip.equals(FoliaPlayerAccess.ip(player))) {
                FoliaDisconnects.disconnect(player, banIpMessage(player, parsed.reason, parsed.expiryTime), "HEOS_IP_BAN");
            }
        }
        return true;
    }

    /**
     * Keeps literal IP addresses untouched, while allowing a player ID to resolve
     * to the last address stored by HEOS.
     */
    private String resolveIp(String target) {
        if (isIpAddress(target)) {
            return target;
        }

        var playerData = storage.loadStored(target);
        if (playerData == null || playerData.lastIp == null || playerData.lastIp.isBlank()) {
            return null;
        }
        return playerData.lastIp;
    }

    private static boolean isIpAddress(String value) {
        if (value.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(value) instanceof Inet6Address;
            } catch (Exception ignored) {
                return false;
            }
        }

        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                if (part.isEmpty() || !part.chars().allMatch(Character::isDigit) || Integer.parseInt(part) > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private boolean unban(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return false;
        }
        boolean removedPlayer = banData.removePlayerBan(args[0]);
        boolean removedIp = banData.removeIpBan(args[0]);
        if (removedPlayer || removedIp) {
            sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.unbanned", args[0]));
        } else {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.notBanned", args[0]));
        }
        return true;
    }

    private boolean unbanIp(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return false;
        }
        if (banData.removeIpBan(args[0])) {
            sender.sendMessage(ChatColor.GREEN + FoliaMessages.text(sender, "text.heos.unbannedIp", args[0]));
        } else {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.notIpBanned", args[0]));
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + FoliaMessages.text(sender, "text.heos.banList"));
        if (banData.playerBans.isEmpty() && banData.ipBans.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + FoliaMessages.text(sender, "text.heos.noBanRecords"));
            return true;
        }
        if (!banData.playerBans.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + FoliaMessages.text(sender, "text.heos.playerBanCount", banData.playerBans.size()));
            for (FoliaBanData.BanEntry ban : banData.playerBans) {
                sender.sendMessage(ChatColor.GRAY + "- " + ban.username + " | " + FoliaTimeParser.formatDuration(sender, ban.expiryTime) + " | " + ban.reason);
            }
        }
        if (!banData.ipBans.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + FoliaMessages.text(sender, "text.heos.ipBanCount", banData.ipBans.size()));
            for (FoliaBanData.IpBanEntry ban : banData.ipBans) {
                sender.sendMessage(ChatColor.GRAY + "- " + ban.ip + " | " + FoliaTimeParser.formatDuration(sender, ban.expiryTime) + " | " + ban.reason);
            }
        }
        return true;
    }

    private static ParsedBan parse(CommandSender sender, String[] args, int start) {
        String defaultReason = FoliaMessages.text(sender, "text.heos.defaultBanReason");
        if (args.length <= start) {
            return new ParsedBan(-1L, defaultReason);
        }

        long parsedTime = FoliaTimeParser.parse(args[start]);
        if (parsedTime != -2L) {
            return new ParsedBan(parsedTime, join(args, start + 1, defaultReason));
        }
        return new ParsedBan(-1L, join(args, start, defaultReason));
    }

    private static String join(String[] args, int start, String fallback) {
        if (start >= args.length) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    public static String banMessage(String reason, long expiryTime) {
        return FoliaMessages.banMessage(reason, FoliaTimeParser.formatAbsolute(expiryTime));
    }

    public static String banMessage(Player player, String reason, long expiryTime) {
        return FoliaMessages.banMessage(player, reason, FoliaTimeParser.formatAbsolute(player, expiryTime));
    }

    public static String banIpMessage(String reason, long expiryTime) {
        return FoliaMessages.banIpMessage(reason, FoliaTimeParser.formatAbsolute(expiryTime));
    }

    public static String banIpMessage(Player player, String reason, long expiryTime) {
        return FoliaMessages.banIpMessage(player, reason, FoliaTimeParser.formatAbsolute(player, expiryTime));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabComplete(sender, command.getName(), args);
    }

    public List<String> tabComplete(CommandSender sender, String subcommand, String[] args) {
        if (!sender.hasPermission("heos.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1 && subcommand.equalsIgnoreCase("ban")) {
            return filter(knownPlayerNames(), args[0]);
        }
        if (args.length == 1 && subcommand.equalsIgnoreCase("unban")) {
            return filter(bannedPlayerNames(), args[0]);
        }
        return Collections.emptyList();
    }

    private Set<String> knownPlayerNames() {
        Set<String> names = new LinkedHashSet<>(storage.usernames());
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private Set<String> bannedPlayerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (FoliaBanData.BanEntry ban : banData.playerBans) {
            names.add(ban.username);
        }
        return names;
    }

    private static List<String> filter(Set<String> values, String prefix) {
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

    private record ParsedBan(long expiryTime, String reason) {
    }
}
