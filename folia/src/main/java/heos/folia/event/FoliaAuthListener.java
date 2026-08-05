package heos.folia.event;

import heos.folia.commands.FoliaBanCommands;
import heos.folia.storage.FoliaBanData;
import heos.folia.storage.FoliaWhitelistData;
import heos.folia.rules.FoliaRuleAgreementService;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import heos.folia.utils.FoliaMessages;
import heos.folia.utils.FoliaMojangApi;
import heos.folia.utils.FoliaTimeParser;

public final class FoliaAuthListener implements Listener {
    private final Plugin plugin;
    private final FoliaAuthService authService;
    private final FoliaRuleAgreementService rulesService;
    private final FoliaBanData banData;
    private final FoliaWhitelistData whitelistData;

    public FoliaAuthListener(Plugin plugin, FoliaAuthService authService, FoliaRuleAgreementService rulesService, FoliaBanData banData, FoliaWhitelistData whitelistData) {
        this.plugin = plugin;
        this.authService = authService;
        this.rulesService = rulesService;
        this.banData = banData;
        this.whitelistData = whitelistData;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        boolean allowMoreCharacters = plugin.getConfig().getBoolean("allowMoreOfflineUsernameCharacters", false);
        boolean allowUnicodeCharacters = plugin.getConfig().getBoolean("allowUnicodeOfflineUsernameCharacters", false);
        if (!FoliaMojangApi.isValidMojangUsername(username)
                && !FoliaMojangApi.isAllowedOfflineUsername(username, allowMoreCharacters, allowUnicodeCharacters)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickComponent(FoliaMessages.offlineNameHint()));
            return;
        }
        if (!authService.areOfflinePlayersAllowed() && !authService.isPremiumUuid(username, event.getUniqueId())) {
            plugin.getLogger().info("Offline player is not allowed: " + username);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickComponent(FoliaMessages.offlineNameHint()));
            return;
        }
        if (plugin.getConfig().getBoolean("enableWhitelist", false) && !whitelistData.isWhitelisted(username)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickComponent(FoliaMessages.whitelistKick()));
            plugin.getLogger().info(FoliaMessages.whitelistDeniedLog(username));
            return;
        }
        if (plugin.getConfig().getBoolean("enableCustomBan", false)) {
            FoliaBanData.BanEntry playerBan = banData.getPlayerBan(username, event.getUniqueId());
            if (playerBan != null) {
                if (FoliaMessages.isMigrationReason(playerBan.reason)) {
                    plugin.getLogger().info(FoliaMessages.migrationBanAttemptLog(username));
                }
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickComponent(FoliaMessages.banMessage(playerBan.reason, FoliaTimeParser.formatAbsolute(playerBan.expiryTime))));
                return;
            }
            String ip = event.getAddress() == null ? "" : event.getAddress().getHostAddress();
            FoliaBanData.IpBanEntry ipBan = banData.getIpBan(ip);
            if (ipBan != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickComponent(FoliaMessages.banIpMessage(ipBan.reason, FoliaTimeParser.formatAbsolute(ipBan.expiryTime))));
            }
        }
    }

    private static Component kickComponent(String message) {
        return Component.text(message == null ? "" : message);
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> authService.prepare(player), null, 1L);
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        authService.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isLocked(event.getPlayer())) {
            return;
        }
        String command = event.getMessage().startsWith("/") ? event.getMessage().substring(1) : event.getMessage();
        if (!authService.canRunCommandWhileLocked(command)) {
            event.setCancelled(true);
            reopenRulesOrPrompt(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onCommandSend(PlayerCommandSendEvent event) {
        if (!authService.isAuthenticationEnabled()) {
            return;
        }
        if (!event.getPlayer().hasPermission("heos.admin")) {
            event.getCommands().remove("heos");
            event.getCommands().remove("ban");
            event.getCommands().remove("ban-ip");
            event.getCommands().remove("unban");
            event.getCommands().remove("unban-ip");
            event.getCommands().remove("banlist");
        }
        if (!authService.isAuthenticated(event.getPlayer())) {
            event.getCommands().remove("changepassword");
            event.getCommands().remove("changepw");
        } else {
            event.getCommands().remove("login");
            event.getCommands().remove("l");
            event.getCommands().remove("register");
            event.getCommands().remove("reg");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onChat(AsyncPlayerChatEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
            reopenRulesOrPrompt(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (isLocked(event.getPlayer()) && event.getFrom().distanceSquared(event.getTo()) > 0.0001D) {
            if (authService.isRulesPending(event.getPlayer())) {
                rulesService.reopen(event.getPlayer(), authService.playerData(event.getPlayer()));
                event.setTo(event.getFrom());
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onInteract(PlayerInteractEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onDrop(PlayerDropItemEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onHeld(PlayerItemHeldEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) block(player, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) block(player, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) block(player, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isLocked(player)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) block(player, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) block(player, event);
    }

    private boolean isLocked(Player player) {
        return authService.shouldBlock(player) || authService.isRulesPending(player);
    }

    private void block(Player player, Cancellable event) {
        if (!isLocked(player)) return;
        event.setCancelled(true);
        reopenRulesOrPrompt(player);
    }

    private void reopenRulesOrPrompt(Player player) {
        if (authService.isRulesPending(player)) {
            rulesService.reopen(player, authService.playerData(player));
        } else {
            authService.sendAuthPrompt(player);
        }
    }
}
