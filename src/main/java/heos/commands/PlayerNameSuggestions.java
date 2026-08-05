package heos.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import heos.Heos;
import heos.storage.BanData;
import heos.storage.PlayerData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class PlayerNameSuggestions {
    private PlayerNameSuggestions() {
    }

    static CompletableFuture<Suggestions> knownPlayers(CommandSourceStack source, SuggestionsBuilder builder) {
        Set<String> names = new LinkedHashSet<>(source.getOnlinePlayerNames());
        names.addAll(PlayerData.usernames());
        return suggest(names, builder);
    }

    static CompletableFuture<Suggestions> whitelistedPlayers(SuggestionsBuilder builder) {
        return suggest(Heos.getWhitelistData().usernames, builder);
    }

    static CompletableFuture<Suggestions> bannedPlayers(SuggestionsBuilder builder) {
        BanData banData = Heos.getBanData();
        Set<String> names = new LinkedHashSet<>();
        for (BanData.BanEntry ban : banData.playerBans) {
            names.add(ban.username);
        }
        return suggest(names, builder);
    }

    private static CompletableFuture<Suggestions> suggest(Collection<String> values, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(values.stream().sorted(String.CASE_INSENSITIVE_ORDER), builder);
    }
}
