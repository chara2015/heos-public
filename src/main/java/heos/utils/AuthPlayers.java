package heos.utils;

import heos.interfaces.PlayerAuth;
import heos.rules.RuleAgreementService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared auth-state predicates for protections that should only apply to real clients.
 */
public final class AuthPlayers {
    private AuthPlayers() {
    }

    public static boolean isRealPlayerWaitingForAuth(Object entity) {
        if (!(entity instanceof ServerPlayer player)
                || !player.getClass().getName().equals(ServerPlayer.class.getName())) {
            return false;
        }

        // PlayerAuth is injected into ServerPlayer by Mixin at runtime.
        // Casting through Object keeps that runtime contract explicit.
        PlayerAuth auth;
        try {
            auth = (PlayerAuth) (Object) player;
        } catch (ClassCastException ignored) {
            return false;
        }
        return (auth.heos$isAuthenticationRequired() && !auth.heos$isAuthenticated())
                || RuleAgreementService.isPending(player);
    }
}
