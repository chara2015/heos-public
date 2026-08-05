package heos.folia.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FoliaTimeParser {
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d+)([smhdy]?)$", Pattern.CASE_INSENSITIVE);

    private FoliaTimeParser() {
    }

    public static long parse(String input) {
        if (input == null || input.isBlank() || input.equals("-1")) {
            return -1L;
        }

        Matcher matcher = TIME_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return -2L;
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        if (unit.isEmpty()) {
            unit = "s";
        }

        long millis = switch (unit) {
            case "s" -> amount * 1000L;
            case "m" -> amount * 60L * 1000L;
            case "h" -> amount * 60L * 60L * 1000L;
            case "d" -> amount * 24L * 60L * 60L * 1000L;
            case "y" -> amount * 365L * 24L * 60L * 60L * 1000L;
            default -> -2L;
        };
        return millis == -2L ? -2L : System.currentTimeMillis() + millis;
    }

    public static String formatDuration(long expiryTime) {
        return formatDuration(null, expiryTime);
    }

    public static String formatDuration(CommandSender sender, long expiryTime) {
        if (expiryTime == -1L) {
            return text(sender, "text.heos.timePermanent");
        }
        long remaining = expiryTime - System.currentTimeMillis();
        if (remaining <= 0L) {
            return text(sender, "text.heos.timeExpiredBan");
        }
        long seconds = remaining / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        long years = days / 365L;
        if (years > 0L) {
            return text(sender, "text.heos.timeYears", years);
        }
        if (days > 0L) {
            return text(sender, "text.heos.timeDays", days);
        }
        if (hours > 0L) {
            return text(sender, "text.heos.timeHours", hours);
        }
        if (minutes > 0L) {
            return text(sender, "text.heos.timeMinutes", minutes);
        }
        return text(sender, "text.heos.timeSeconds", seconds);
    }

    public static String formatAbsolute(long expiryTime) {
        if (expiryTime == -1L) {
            return FoliaMessages.translate("text.heos.timePermanent");
        }
        return formatDateTime(null, expiryTime);
    }

    public static String formatAbsolute(Player player, long expiryTime) {
        if (expiryTime == -1L) {
            return FoliaMessages.text(player, "text.heos.timePermanent");
        }
        return formatDateTime(player, expiryTime);
    }

    public static String formatDateTime(CommandSender sender, long time) {
        return new SimpleDateFormat(text(sender, "text.heos.dateTimeFormat")).format(new Date(time));
    }

    private static String text(CommandSender sender, String key, Object... args) {
        return sender == null ? FoliaMessages.translate(key).formatted(args) : FoliaMessages.text(sender, key, args);
    }
}
