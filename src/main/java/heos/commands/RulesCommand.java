package heos.commands;

import com.mojang.brigadier.CommandDispatcher;
import heos.rules.RuleAgreementService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** Commands invoked by the interactive buttons in the rules book. */
public final class RulesCommand {
    private RulesCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rules")
                .then(Commands.literal("agree").executes(context -> RuleAgreementService.agree(context.getSource().getPlayerOrException())))
                .then(Commands.literal("decline").executes(context -> RuleAgreementService.decline(context.getSource().getPlayerOrException())))
                .then(Commands.literal("done").executes(context -> RuleAgreementService.complete(context.getSource().getPlayerOrException()))));
    }
}
