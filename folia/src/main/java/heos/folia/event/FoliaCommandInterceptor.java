package heos.folia.event;

import heos.folia.commands.FoliaBanCommands;
import heos.folia.commands.FoliaMigrationCommands;
import heos.folia.rules.FoliaRuleAgreementService;
import heos.folia.utils.FoliaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Arrays;
import java.util.Locale;

public final class FoliaCommandInterceptor implements Listener {
    private final FoliaAuthService authService;
    private final FoliaRuleAgreementService rulesService;
    private final FoliaBanCommands banCommands;
    private final FoliaMigrationCommands migrationCommands;

    public FoliaCommandInterceptor(FoliaAuthService authService, FoliaRuleAgreementService rulesService, FoliaBanCommands banCommands, FoliaMigrationCommands migrationCommands) {
        this.authService = authService;
        this.rulesService = rulesService;
        this.banCommands = banCommands;
        this.migrationCommands = migrationCommands;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        ParsedCommand parsed = parse(event.getMessage());
        if (parsed == null || !execute(event.getPlayer(), parsed.root, parsed.args)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onServerCommand(ServerCommandEvent event) {
        ParsedCommand parsed = parse(event.getCommand());
        if (parsed == null || !execute(event.getSender(), parsed.root, parsed.args)) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean execute(CommandSender sender, String root, String[] args) {
        return switch (root) {
            case "login", "l" -> login(sender, args);
            case "register", "reg" -> register(sender, args);
            case "changepassword", "changepw" -> changePassword(sender, args);
            case "rules" -> rules(sender, args);
            case "heos-internal-migration" -> internalMigration(sender, args);
            case "ban", "ban-ip", "unban", "unban-ip", "banlist" -> banCommands.onSubcommand(sender, root, args);
            case "pardon" -> banCommands.onSubcommand(sender, "unban", args);
            case "pardon-ip" -> banCommands.onSubcommand(sender, "unban-ip", args);
            default -> false;
        };
    }

    private boolean internalMigration(CommandSender sender, String[] args) {
        if (sender instanceof Player player && args.length == 2) {
            migrationCommands.handleInternalAction(player, args[0], args[1]);
        }
        return true;
    }

    private boolean login(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.playerOnlyCommand"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageLogin"));
            return true;
        }
        authService.login(player, args[0]);
        return true;
    }

    private boolean register(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.playerOnlyCommand"));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageRegister"));
            return true;
        }
        authService.register(player, args[0], args[1]);
        return true;
    }

    private boolean changePassword(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.playerOnlyCommand"));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + FoliaMessages.text(sender, "text.heos.usageChangePassword"));
            return true;
        }
        authService.changePassword(player, args[0], args[1]);
        return true;
    }

    private boolean rules(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return true;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "agree" -> {
                if (rulesService.agree(player, authService.playerData(player))) {
                    authService.sendAuthPrompt(player);
                }
                yield true;
            }
            case "decline" -> {
                rulesService.decline(player);
                yield true;
            }
            case "done" -> rulesService.complete(player, authService.playerData(player));
            default -> true;
        };
    }

    private static ParsedCommand parse(String commandLine) {
        String normalized = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String[] split = normalized.split("\\s+");
        String root = split[0].toLowerCase(Locale.ROOT);
        String[] args = split.length == 1 ? new String[0] : Arrays.copyOfRange(split, 1, split.length);
        return new ParsedCommand(root, args);
    }

    private record ParsedCommand(String root, String[] args) {
    }
}
