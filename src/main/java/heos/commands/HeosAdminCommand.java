package heos.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import heos.Heos;
import heos.integrations.Permissions;
import heos.storage.PlayerData;
import heos.storage.WhitelistData;
import heos.utils.HeosLogger;
import heos.utils.Messages;
import heos.utils.PasswordHasher;
import heos.utils.TimeParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin commands for managing player accounts
 */
public class HeosAdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("heos")
                .requires(Permissions.requireLevel(3))
                .then(Commands.literal("resetpassword")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> PlayerNameSuggestions.knownPlayers(context.getSource(), builder))
                        .then(Commands.argument("newPassword", StringArgumentType.string())
                            .executes(HeosAdminCommand::resetPassword)
                        )
                    )
                )
                .then(Commands.literal("info")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> PlayerNameSuggestions.knownPlayers(context.getSource(), builder))
                        .executes(HeosAdminCommand::info)
                    )
                )
                .then(Commands.literal("migrate")
                    .requires(source -> Heos.getConfig().enablePlayerDataMigration)
                    .then(Commands.argument("sourcePlayer", StringArgumentType.string())
                        .suggests((context, builder) -> PlayerNameSuggestions.knownPlayers(context.getSource(), builder))
                        .then(Commands.argument("targetPlayer", StringArgumentType.string())
                            .suggests((context, builder) -> PlayerNameSuggestions.knownPlayers(context.getSource(), builder))
                            .executes(MigrateCommand::prepareMigrate)
                        )
                    )
                )
                .then(Commands.literal("whitelist")
                    .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.string())
                            .executes(HeosAdminCommand::whitelistAdd)
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.string())
                            .suggests((context, builder) -> PlayerNameSuggestions.whitelistedPlayers(builder))
                            .executes(HeosAdminCommand::whitelistRemove)
                        )
                    )
                    .then(Commands.literal("list")
                        .executes(HeosAdminCommand::whitelistList)
                    )
                )
        );
    }

    private static int resetPassword(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String targetUsername = StringArgumentType.getString(context, "player");
        String newPassword = StringArgumentType.getString(context, "newPassword");
        return resetPassword(source, targetUsername, newPassword);
    }

    public static int resetPassword(CommandSourceStack source, String targetUsername, String newPassword) {
        if (newPassword.length() < Heos.getConfig().minPasswordLength) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.newPasswordTooShort", Heos.getConfig().minPasswordLength)));
            return 0;
        }

        if (newPassword.length() > Heos.getConfig().maxPasswordLength) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.newPasswordTooLong", Heos.getConfig().maxPasswordLength)));
            return 0;
        }

        PlayerData data = Heos.getPlayerData(targetUsername);
        if (!data.isRegistered()) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.adminPlayerNotRegistered", targetUsername)));
            return 0;
        }

        String newPasswordHash = PasswordHasher.hashPassword(newPassword);
        if (newPasswordHash == null) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.adminResetPasswordFailed")));
            HeosLogger.error("Failed to hash password for " + targetUsername);
            return 0;
        }

        data.passwordHash = newPasswordHash;
        data.save();

        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.adminResetPasswordSuccess", targetUsername)), true);
        HeosLogger.info("Admin " + source.getTextName() + " reset password for " + targetUsername);

        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(targetUsername);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Component.literal(Messages.text(targetPlayer, "text.heos.passwordResetNotice")), false);
            targetPlayer.sendSystemMessage(Component.literal(Messages.text(targetPlayer, "text.heos.newPasswordNotice", newPassword)), false);
            targetPlayer.sendSystemMessage(Component.literal(Messages.text(targetPlayer, "text.heos.changePasswordSoon")), false);
        }

        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String targetUsername = StringArgumentType.getString(context, "player");

        PlayerData data = PlayerData.loadStored(targetUsername);
        if (data == null) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.playerNoStoredData", targetUsername)));
            return 0;
        }

        String unknown = Messages.text(source, "text.heos.unknownValue");
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.playerInfoTitle", data.username)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.playerInfoUuid", data.uuid != null ? data.uuid.toString() : unknown)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.playerInfoLastIp", data.lastIp != null && !data.lastIp.isEmpty() ? data.lastIp : unknown)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.playerInfoRegisteredAt", data.registeredTime > 0 ? TimeParser.formatDateTime(source, data.registeredTime) : unknown)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.playerInfoLastLogin", data.lastLoginTime > 0 ? TimeParser.formatDateTime(source, data.lastLoginTime) : unknown)), false);
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.playerInfoAccountType", Messages.text(source, data.isOnlineAccount ? "text.heos.accountPremium" : "text.heos.accountOffline"))), false);

        return 1;
    }

    private static int whitelistAdd(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String username = StringArgumentType.getString(context, "player");
        WhitelistData whitelistData = Heos.getWhitelistData();
        boolean addedHeos = whitelistData.add(username);

        if (addedHeos) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.whitelistAdded", username)), true);
            return 1;
        }

        source.sendFailure(Component.literal(Messages.text(source, "text.heos.whitelistAlreadyAdded", username)));
        return 0;
    }

    private static int whitelistRemove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String username = StringArgumentType.getString(context, "player");
        WhitelistData whitelistData = Heos.getWhitelistData();
        boolean removedHeos = whitelistData.remove(username);

        if (removedHeos) {
            source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.whitelistRemoved", username)), true);
            return 1;
        }

        source.sendFailure(Component.literal(Messages.text(source, "text.heos.whitelistNotAdded", username)));
        return 0;
    }

    private static int whitelistList(CommandContext<CommandSourceStack> context) {
        WhitelistData whitelistData = Heos.getWhitelistData();
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(Messages.text(source, "text.heos.whitelistSize", whitelistData.usernames.size())), false);
        if (!whitelistData.usernames.isEmpty()) {
            source.sendSuccess(() -> Component.literal(String.join(", ", whitelistData.usernames)), false);
        }
        return 1;
    }
}
