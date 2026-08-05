package heos.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import heos.Heos;
import heos.integrations.Permissions;
import heos.storage.BanData;
import heos.storage.PlayerData;
import heos.utils.HeosLogger;
import heos.utils.Messages;
import heos.utils.TimeParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
public class BanCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!Heos.getConfig().enableCustomBan) {
            dispatcher.register(customUnbanCommand());
            dispatcher.register(customUnbanIpCommand());
            HeosLogger.debug("Custom ban disabled, keeping vanilla ban commands");
            return;
        }

        removeVanillaBanCommands(dispatcher);

        dispatcher.register(customBanCommand());
        dispatcher.register(customBanIpCommand());
        dispatcher.register(customUnbanCommand());
        dispatcher.register(customUnbanIpCommand());
        dispatcher.register(
            Commands.literal("banlist")
                .requires(Permissions.requireLevel(3))
                .executes(BanCommands::listBans)
        );
        HeosLogger.debug("Registered custom ban commands");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> customBanCommand() {
        return Commands.literal("ban")
                .requires(Permissions.requireLevel(3))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> PlayerNameSuggestions.knownPlayers(context.getSource(), builder))
                        .executes(ctx -> banPlayer(ctx, null, Messages.text(ctx.getSource(), "text.heos.defaultBanReason")))
                        .then(Commands.argument("durationOrReason", StringArgumentType.string())
                                .executes(ctx -> banPlayerFlexible(ctx,
                                        StringArgumentType.getString(ctx, "durationOrReason"),
                                        null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> banPlayerFlexible(ctx,
                                                StringArgumentType.getString(ctx, "durationOrReason"),
                                                StringArgumentType.getString(ctx, "reason")))
                                )
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> customUnbanCommand() {
        return Commands.literal("unban")
                .requires(Permissions.requireLevel(3))
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests((context, builder) -> PlayerNameSuggestions.bannedPlayers(builder))
                        .executes(BanCommands::unbanPlayer)
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> customUnbanIpCommand() {
        return Commands.literal("unban-ip")
                .requires(Permissions.requireLevel(3))
                .then(Commands.argument("ip", StringArgumentType.string())
                        .executes(BanCommands::unbanIp)
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> customBanIpCommand() {
        return Commands.literal("ban-ip")
                .requires(Permissions.requireLevel(3))
                .then(Commands.argument("ip", StringArgumentType.string())
                        .executes(ctx -> banIp(ctx, null, Messages.text(ctx.getSource(), "text.heos.defaultBanReason")))
                        .then(Commands.argument("durationOrReason", StringArgumentType.string())
                                .executes(ctx -> banIpFlexible(ctx,
                                        StringArgumentType.getString(ctx, "durationOrReason"),
                                        null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> banIpFlexible(ctx,
                                                StringArgumentType.getString(ctx, "durationOrReason"),
                                                StringArgumentType.getString(ctx, "reason")))
                                )
                        )
                );
    }

    private static int banPlayerFlexible(CommandContext<CommandSourceStack> context, String durationOrReason, String reasonTail) {
        ParsedBanArgs args = parseBanArgs(context.getSource(), durationOrReason, reasonTail);
        return banPlayer(context, args.timeStr, args.reason);
    }

    private static int banPlayer(CommandContext<CommandSourceStack> context, String timeStr, String reason) {
        CommandSourceStack source = context.getSource();
        String targetUsername = StringArgumentType.getString(context, "player");

        long expiryTime = -1;
        if (timeStr != null) {
            expiryTime = TimeParser.parseTime(timeStr);
            if (expiryTime == -2) {
                source.sendFailure(Component.literal(Messages.text(source, "text.heos.invalidBanTime")));
                return 0;
            }
        }

        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(targetUsername);
        UUID targetUuid = targetPlayer != null ? targetPlayer.getUUID() : null;

        BanData banData = Heos.getBanData();
        banData.addPlayerBan(targetUsername, targetUuid, reason, expiryTime, source.getTextName());

        final String timeInfo = TimeParser.formatExpiryTime(source, expiryTime);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.bannedPlayer", targetUsername)), true);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.banReason", reason)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.banDuration", timeInfo)), false);

        HeosLogger.info(source.getTextName() + " banned " + targetUsername + " for " + timeInfo + " - Reason: " + reason);

        if (targetPlayer != null) {
            String kickMessage = Messages.banMessage(targetPlayer, reason, TimeParser.formatAbsoluteTime(targetPlayer, expiryTime));
            targetPlayer.connection.disconnect(Component.literal(kickMessage));
        }

        return 1;
    }

    static int banPlayerProgrammatic(String username, UUID uuid, String reason, long expiryTime, String bannedBy) {
        BanData banData = Heos.getBanData();
        banData.addPlayerBan(username, uuid, reason, expiryTime, bannedBy);
        return 1;
    }

    private static int banIpFlexible(CommandContext<CommandSourceStack> context, String durationOrReason, String reasonTail) {
        ParsedBanArgs args = parseBanArgs(context.getSource(), durationOrReason, reasonTail);
        return banIp(context, args.timeStr, args.reason);
    }

    private static int banIp(CommandContext<CommandSourceStack> context, String timeStr, String reason) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "ip");
        String targetIp = resolveIp(target);
        if (targetIp == null) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.playerIpNotFound", target)));
            return 0;
        }

        long expiryTime = -1;
        if (timeStr != null) {
            expiryTime = TimeParser.parseTime(timeStr);
            if (expiryTime == -2) {
                source.sendFailure(Component.literal(Messages.text(source, "text.heos.invalidBanTime")));
                return 0;
            }
        }

        BanData banData = Heos.getBanData();
        banData.addIpBan(targetIp, reason, expiryTime, source.getTextName());

        final String timeInfo = TimeParser.formatExpiryTime(source, expiryTime);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.bannedIp", targetIp)), true);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.banReason", reason)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.banDuration", timeInfo)), false);

        HeosLogger.info(source.getTextName() + " banned IP " + targetIp + " for " + timeInfo + " - Reason: " + reason);

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (targetIp.equals(player.getIpAddress())) {
                String kickMessage = Messages.banIpMessage(player, reason, TimeParser.formatAbsoluteTime(player, expiryTime));
                player.connection.disconnect(Component.literal(kickMessage));
            }
        }

        return 1;
    }

    /**
     * Keeps literal IP addresses untouched, while allowing a player ID to resolve
     * to the last address stored by HEOS.
     */
    private static String resolveIp(String target) {
        if (isIpAddress(target)) {
            return target;
        }

        PlayerData playerData = PlayerData.loadStored(target);
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

    private static ParsedBanArgs parseBanArgs(CommandSourceStack source, String durationOrReason, String reasonTail) {
        if (durationOrReason == null) {
            return new ParsedBanArgs(null, Messages.text(source, "text.heos.defaultBanReason"));
        }

        if (TimeParser.parseTime(durationOrReason) != -2) {
            return new ParsedBanArgs(durationOrReason, reasonTail == null ? Messages.text(source, "text.heos.defaultBanReason") : reasonTail);
        }

        String reason = reasonTail == null ? durationOrReason : durationOrReason + " " + reasonTail;
        return new ParsedBanArgs(null, reason);
    }

    private static int unbanPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "target");

        BanData banData = Heos.getBanData();
        boolean removedPlayer = banData.removePlayerBan(target);
        boolean removedIp = banData.removeIpBan(target);
        if (removedPlayer || removedIp) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.unbanned", target)), true);
            HeosLogger.info(source.getTextName() + " unbanned " + target);
            return 1;
        }

        source.sendFailure(Component.literal(Messages.text(source, "text.heos.notBanned", target)));
        return 0;
    }

    private static int unbanIp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String targetIp = StringArgumentType.getString(context, "ip");

        BanData banData = Heos.getBanData();
        if (banData.removeIpBan(targetIp)) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.unbannedIp", targetIp)), true);
            HeosLogger.info(source.getTextName() + " unbanned IP " + targetIp);
            return 1;
        }

        source.sendFailure(Component.literal(Messages.text(source, "text.heos.notIpBanned", targetIp)));
        return 0;
    }

    private static int listBans(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BanData banData = Heos.getBanData();
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.banList")), false);

        if (banData.playerBans.isEmpty() && banData.ipBans.isEmpty()) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.noBanRecords")), false);
            return 1;
        }

        if (!banData.playerBans.isEmpty()) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.playerBanCount", banData.playerBans.size())), false);
            for (BanData.BanEntry ban : banData.playerBans) {
                final String ti = TimeParser.formatExpiryTime(source, ban.expiryTime);
                source.sendSuccess(() -> Component.literal("- " + ban.username + " | " + ti + " | " + ban.reason), false);
            }
        }

        if (!banData.ipBans.isEmpty()) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.ipBanCount", banData.ipBans.size())), false);
            for (BanData.IpBanEntry ban : banData.ipBans) {
                final String ti = TimeParser.formatExpiryTime(source, ban.expiryTime);
                source.sendSuccess(() -> Component.literal("- " + ban.ip + " | " + ti + " | " + ban.reason), false);
            }
        }

        return 1;
    }

    private static void removeVanillaBanCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        removeRootCommand(dispatcher, "ban");
        removeRootCommand(dispatcher, "pardon");
        removeRootCommand(dispatcher, "ban-ip");
        removeRootCommand(dispatcher, "pardon-ip");
    }

    @SuppressWarnings("unchecked")
    private static void removeRootCommand(CommandDispatcher<CommandSourceStack> dispatcher, String command) {
        try {
            CommandNode<CommandSourceStack> root = dispatcher.getRoot();
            removeFromCommandMap(root, "children", command);
            removeFromCommandMap(root, "literals", command);
            removeFromCommandMap(root, "arguments", command);
        } catch (ReflectiveOperationException e) {
            HeosLogger.warn("Failed to remove vanilla command /" + command + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromCommandMap(CommandNode<CommandSourceStack> node, String fieldName, String command) throws ReflectiveOperationException {
        Field field = CommandNode.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, CommandNode<CommandSourceStack>> map = (Map<String, CommandNode<CommandSourceStack>>) field.get(node);
        map.remove(command.toLowerCase(Locale.ROOT));
    }

    private record ParsedBanArgs(String timeStr, String reason) {
    }
}
