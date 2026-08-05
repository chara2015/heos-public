package heos.commands;

import heos.integrations.Permissions;
import heos.utils.AuthPlayers;
import heos.utils.Messages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SensitiveCommandHandler {
    private SensitiveCommandHandler() {
    }

    public static InteractionResult handle(ServerPlayer player, String command) {
        List<String> parts = split(command);
        if (parts.isEmpty()) {
            return InteractionResult.PASS;
        }

        String root = parts.get(0).toLowerCase(Locale.ROOT);
        if ("heos-internal-migration".equals(root)) {
            if (parts.size() == 3) {
                MigrateCommand.handleInternalAction(player, parts.get(1), parts.get(2));
            }
            return InteractionResult.FAIL;
        }

        if ("login".equals(root) || "l".equals(root)) {
            if (parts.size() != 2) {
                player.sendSystemMessage(Component.literal(Messages.text(player, "text.heos.usageLogin")), false);
                return InteractionResult.FAIL;
            }
            LoginCommand.execute(player, parts.get(1));
            return InteractionResult.FAIL;
        }

        if ("register".equals(root) || "reg".equals(root)) {
            if (parts.size() != 3) {
                player.sendSystemMessage(Component.literal(Messages.text(player, "text.heos.usageRegister")), false);
                return InteractionResult.FAIL;
            }
            RegisterCommand.execute(player, parts.get(1), parts.get(2));
            return InteractionResult.FAIL;
        }

        if ("changepassword".equals(root) || "changepw".equals(root)) {
            if (AuthPlayers.isRealPlayerWaitingForAuth(player)) {
                return InteractionResult.PASS;
            }
            if (parts.size() != 3) {
                player.sendSystemMessage(Component.literal(Messages.text(player, "text.heos.usageChangePassword")), false);
                return InteractionResult.FAIL;
            }
            ChangePasswordCommand.execute(player, parts.get(1), parts.get(2));
            return InteractionResult.FAIL;
        }

        if ("heos".equals(root) && parts.size() >= 2 && "resetpassword".equalsIgnoreCase(parts.get(1))) {
            if (!Permissions.requireLevel(3).test(player.createCommandSourceStack())) {
                player.sendSystemMessage(Component.literal(Messages.text(player, "text.heos.noPermission")), false);
                return InteractionResult.FAIL;
            }
            if (parts.size() != 4) {
                player.sendSystemMessage(Component.literal(Messages.text(player, "text.heos.usageResetPassword")), false);
                return InteractionResult.FAIL;
            }
            HeosAdminCommand.resetPassword(player.createCommandSourceStack(), parts.get(2), parts.get(3));
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    private static List<String> split(String command) {
        List<String> parts = new ArrayList<>();
        for (String part : command.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }
}
