package heos.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import heos.interfaces.PlayerAuth;
import heos.storage.PlayerData;
import heos.utils.HeosLogger;
import heos.utils.Messages;
import heos.utils.PasswordHasher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Change password command
 */
public class ChangePasswordCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("changepassword")
                .requires(ChangePasswordCommand::canUse)
                .then(Commands.argument("oldPassword", StringArgumentType.string())
                    .then(Commands.argument("newPassword", StringArgumentType.string())
                        .executes(ChangePasswordCommand::execute)
                    )
                )
        );
        
        // Alias
        dispatcher.register(
            Commands.literal("changepw")
                .requires(ChangePasswordCommand::canUse)
                .then(Commands.argument("oldPassword", StringArgumentType.string())
                    .then(Commands.argument("newPassword", StringArgumentType.string())
                        .executes(ChangePasswordCommand::execute)
                    )
                )
        );
    }

    private static boolean canUse(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayer();
            PlayerAuth auth = (PlayerAuth) player;
            return auth.heos$isAuthenticated() && !auth.heos$canSkipAuth();
        } catch (Exception ignored) {
            return false;
        }
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal(Messages.text(source, "text.heos.playerOnlyCommand")));
            return 0;
        }
        
        ServerPlayer player = source.getPlayer();
        String oldPassword = StringArgumentType.getString(context, "oldPassword");
        String newPassword = StringArgumentType.getString(context, "newPassword");
        return execute(player, oldPassword, newPassword);
    }

    public static int execute(ServerPlayer player, String oldPassword, String newPassword) {
        PlayerAuth playerAuth = (PlayerAuth) player;

        if (!playerAuth.heos$isAuthenticated()) {
            player.sendSystemMessage(Component.literal(Messages.changePasswordLoginRequired(player)), false);
            return 0;
        }
        
        // Check if player can skip auth (premium player)
        if (playerAuth.heos$canSkipAuth()) {
            player.sendSystemMessage(Component.literal(Messages.premiumNoPasswordChange(player)), false);
            return 0;
        }
        
        PlayerData data = playerAuth.heos$getPlayerData();
        
        // Check if registered
        if (!data.isRegistered()) {
            player.sendSystemMessage(Component.literal(Messages.changePasswordNotRegistered(player)), false);
            return 0;
        }
        
        // Verify old password
        if (!PasswordHasher.verifyPassword(oldPassword, data.passwordHash)) {
            player.sendSystemMessage(Component.literal(Messages.oldPasswordIncorrect(player)), false);
            HeosLogger.warn("Player " + player.getName().getString() + " failed to change password (wrong old password)");
            return 0;
        }
        
        // Validate new password length
        if (newPassword.length() < heos.Heos.getConfig().minPasswordLength) {
            player.sendSystemMessage(Component.literal(Messages.newPasswordTooShort(player, heos.Heos.getConfig().minPasswordLength)), false);
            return 0;
        }
        
        if (newPassword.length() > heos.Heos.getConfig().maxPasswordLength) {
            player.sendSystemMessage(Component.literal(Messages.newPasswordTooLong(player, heos.Heos.getConfig().maxPasswordLength)), false);
            return 0;
        }

        // Check if new password is same as old
        if (oldPassword.equals(newPassword)) {
            player.sendSystemMessage(Component.literal(Messages.newPasswordSameAsOld(player)), false);
            return 0;
        }
        
        // Hash new password and save
        String newPasswordHash = PasswordHasher.hashPassword(newPassword);
        if (newPasswordHash == null) {
            player.sendSystemMessage(Component.literal(Messages.changePasswordFailed(player)), false);
            HeosLogger.error("Failed to hash new password for " + player.getName().getString());
            return 0;
        }
        
        data.passwordHash = newPasswordHash;
        data.save();
        
        player.sendSystemMessage(Component.literal(Messages.changePasswordSuccess(player)), false);
        player.sendSystemMessage(Component.literal(Messages.keepPasswordSafe(player)), false);
        HeosLogger.info("Player " + player.getName().getString() + " changed password successfully");
        
        return 1;
    }
}



