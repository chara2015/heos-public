package heos.utils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and formatting ban durations.
 */
public class TimeParser {
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d+)([smhdy]?)$", Pattern.CASE_INSENSITIVE);

    public static long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return -1;
        }

        timeStr = timeStr.trim();
        if (timeStr.equals("-1")) {
            return -1;
        }

        Matcher matcher = TIME_PATTERN.matcher(timeStr);
        if (!matcher.matches()) {
            return -2;
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();
        if (unit.isEmpty()) {
            unit = "s";
        }

        long milliseconds = switch (unit) {
            case "s" -> amount * 1000L;
            case "m" -> amount * 60L * 1000L;
            case "h" -> amount * 60L * 60L * 1000L;
            case "d" -> amount * 24L * 60L * 60L * 1000L;
            case "y" -> amount * 365L * 24L * 60L * 60L * 1000L;
            default -> -2L;
        };
        return milliseconds == -2L ? -2L : System.currentTimeMillis() + milliseconds;
    }

    public static String formatExpiryTime(long expiryTime) {
        if (expiryTime == -1) {
            return Messages.text("text.heos.timePermanent");
        }

        long remaining = expiryTime - System.currentTimeMillis();
        if (remaining <= 0) {
            return Messages.text("text.heos.timeExpiredBan");
        }

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long years = days / 365;
        if (years > 0) {
            return Messages.text("text.heos.timeYears", years);
        }
        if (days > 0) {
            return Messages.text("text.heos.timeDays", days);
        }
        if (hours > 0) {
            return Messages.text("text.heos.timeHours", hours);
        }
        if (minutes > 0) {
            return Messages.text("text.heos.timeMinutes", minutes);
        }
        return Messages.text("text.heos.timeSeconds", seconds);
    }

    public static String formatExpiryTime(CommandSourceStack source, long expiryTime) {
        if (expiryTime == -1) {
            return Messages.text(source, "text.heos.timePermanent");
        }

        long remaining = expiryTime - System.currentTimeMillis();
        if (remaining <= 0) {
            return Messages.text(source, "text.heos.timeExpiredBan");
        }

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long years = days / 365;
        if (years > 0) {
            return Messages.text(source, "text.heos.timeYears", years);
        }
        if (days > 0) {
            return Messages.text(source, "text.heos.timeDays", days);
        }
        if (hours > 0) {
            return Messages.text(source, "text.heos.timeHours", hours);
        }
        if (minutes > 0) {
            return Messages.text(source, "text.heos.timeMinutes", minutes);
        }
        return Messages.text(source, "text.heos.timeSeconds", seconds);
    }

    public static String formatAbsoluteTime(long expiryTime) {
        if (expiryTime == -1) {
            return Messages.text("text.heos.timePermanent");
        }
        return formatDateTime(expiryTime);
    }

    public static String formatAbsoluteTime(ServerPlayer player, long expiryTime) {
        if (expiryTime == -1) {
            return Messages.text(player, "text.heos.timePermanent");
        }
        return formatDateTime(player, expiryTime);
    }

    public static String formatDateTime(long time) {
        return new java.text.SimpleDateFormat(Messages.text("text.heos.dateTimeFormat")).format(new java.util.Date(time));
    }

    public static String formatDateTime(CommandSourceStack source, long time) {
        return new java.text.SimpleDateFormat(Messages.text(source, "text.heos.dateTimeFormat")).format(new java.util.Date(time));
    }

    public static String formatDateTime(ServerPlayer player, long time) {
        return new java.text.SimpleDateFormat(Messages.text(player, "text.heos.dateTimeFormat")).format(new java.util.Date(time));
    }
}
