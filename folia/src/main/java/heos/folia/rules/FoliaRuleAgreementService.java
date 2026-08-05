package heos.folia.rules;

import heos.folia.storage.FoliaPlayerData;
import heos.folia.storage.FoliaStorage;
import heos.folia.utils.FoliaMessages;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Folia-native presentation and persistence for the rules acknowledgement. */
public final class FoliaRuleAgreementService {
    private static final int BOOK_PAGE_LINES = 14;
    private static final int BOOK_LINE_UNITS = 20;

    private final Plugin plugin;
    private final FoliaStorage storage;
    private final Set<UUID> openedBooks = ConcurrentHashMap.newKeySet();

    public FoliaRuleAgreementService(Plugin plugin, FoliaStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public boolean isPending(FoliaPlayerData data) {
        return isEnabled() && requiresAgreement() && data != null && !data.hasAcceptedRules;
    }

    public boolean needsBook(FoliaPlayerData data) {
        if (!isEnabled() || data == null) return false;
        return requiresAgreement() ? !data.hasAcceptedRules : !data.hasSeenRulesBook && !data.hasAcceptedRules;
    }

    public void openIfNeeded(Player player, FoliaPlayerData data) {
        if (!needsBook(data) || !openedBooks.add(player.getUniqueId())) return;
        open(player);
    }

    public void reopen(Player player, FoliaPlayerData data) {
        if (player == null || !needsBook(data)) return;
        openedBooks.remove(player.getUniqueId());
        openIfNeeded(player, data);
    }

    public boolean agree(Player player, FoliaPlayerData data) {
        if (!isEnabled() || data == null || data.hasAcceptedRules) return false;
        data.hasAcceptedRules = true;
        data.hasSeenRulesBook = true;
        storage.save(data);
        openedBooks.remove(player.getUniqueId());
        player.sendMessage(FoliaMessages.text(player, "text.heos.rulesAccepted"));
        return true;
    }

    public void decline(Player player) {
        openedBooks.remove(player.getUniqueId());
        player.kick(net.kyori.adventure.text.Component.text(FoliaMessages.text(player, "text.heos.rulesDeclined")));
    }

    public boolean complete(Player player, FoliaPlayerData data) {
        if (!isEnabled() || requiresAgreement() || data == null || data.hasSeenRulesBook || data.hasAcceptedRules) return false;
        data.hasSeenRulesBook = true;
        storage.save(data);
        openedBooks.remove(player.getUniqueId());
        return true;
    }

    private void open(Player player) {
        List<String> configuredPages = new ArrayList<>();
        for (String page : plugin.getConfig().getString("rules.pages", "Welcome to the server!").split("\\|", -1)) {
            configuredPages.add(page.replace("\\n", "\n"));
        }
        String prompt = FoliaMessages.text(player, "text.heos.rulesPrompt");
        String labels = requiresAgreement()
                ? FoliaMessages.text(player, "text.heos.rulesAccept") + "\n" + FoliaMessages.text(player, "text.heos.rulesDecline")
                : FoliaMessages.text(player, "text.heos.rulesDone");
        BaseComponent buttons = buttons(player, prompt);

        List<BaseComponent> pages = new ArrayList<>();
        int last = configuredPages.size() - 1;
        for (int index = 0; index < configuredPages.size(); index++) {
            String page = configuredPages.get(index);
            if (index == last && fitsOnPage(page + "\n\n" + prompt + "\n\n" + labels)) {
                TextComponent combined = new TextComponent(page + "\n\n");
                combined.addExtra(buttons);
                pages.add(combined);
            } else {
                pages.add(new TextComponent(page));
                if (index == last) pages.add(buttons);
            }
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(FoliaMessages.text(player, "text.heos.rulesBookTitle"));
        meta.setAuthor("Heos");
        meta.spigot().setPages(pages.toArray(BaseComponent[]::new));
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private BaseComponent buttons(Player player, String prompt) {
        TextComponent buttons = new TextComponent(prompt + "\n\n");
        if (requiresAgreement()) {
            TextComponent agree = new TextComponent(FoliaMessages.text(player, "text.heos.rulesAccept"));
            agree.setBold(true);
            agree.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules agree"));
            TextComponent decline = new TextComponent(FoliaMessages.text(player, "text.heos.rulesDecline"));
            decline.setBold(true);
            decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules decline"));
            buttons.addExtra(agree);
            buttons.addExtra("\n");
            buttons.addExtra(decline);
        } else {
            TextComponent done = new TextComponent(FoliaMessages.text(player, "text.heos.rulesDone"));
            done.setBold(true);
            done.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rules done"));
            buttons.addExtra(done);
        }
        return buttons;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("rules.enabled", false);
    }

    private boolean requiresAgreement() {
        return plugin.getConfig().getBoolean("rules.require-agreement", true);
    }

    private static boolean fitsOnPage(String text) {
        int lines = 1;
        int width = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                lines++;
                width = 0;
                continue;
            }
            int units = codePoint <= 0x7F ? 1 : 2;
            if (width + units > BOOK_LINE_UNITS) {
                lines++;
                width = 0;
            }
            width += units;
        }
        return lines <= BOOK_PAGE_LINES;
    }
}
