package heos.rules;

import heos.Heos;
import heos.interfaces.PlayerAuth;
import heos.storage.PlayerData;
import heos.utils.Messages;
//? if >= 1.20.5 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
//? if >= 1.20.5 {
import net.minecraft.server.network.Filterable;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
*///?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >= 1.20.5 {
import net.minecraft.world.item.component.WrittenBookContent;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Presents and records the server rules acknowledgement. */
public final class RuleAgreementService {
    private static final Set<UUID> OPENED_BOOKS = ConcurrentHashMap.newKeySet();
    private static final int BOOK_PAGE_LINES = 14;
    private static final int BOOK_LINE_UNITS = 20;

    private RuleAgreementService() { }

    public static boolean isPending(ServerPlayer player) {
        return Heos.getConfig().requireRulesAgreement && needsBook(player);
    }

    public static boolean needsBook(ServerPlayer player) {
        if (player == null || !Heos.getConfig().enableRulesBook) return false;
        PlayerData data = ((PlayerAuth) player).heos$getPlayerData();
        if (data == null) return false;
        return Heos.getConfig().requireRulesAgreement
                ? !data.hasAcceptedRules
                : !data.hasSeenRulesBook && !data.hasAcceptedRules;
    }

    public static void onAuthenticationComplete(ServerPlayer player) {
        if (!needsBook(player)) return;
        OPENED_BOOKS.remove(player.getUUID());
        player.level().getServer().execute(() -> openBook(player));
    }

    /** Shows the rules before an offline player starts the password flow. */
    public static boolean showBeforeAuthentication(ServerPlayer player) {
        if (!needsBook(player)) return false;
        OPENED_BOOKS.remove(player.getUUID());
        player.level().getServer().execute(() -> openBook(player));
        return true;
    }

    public static void openBook(ServerPlayer player) {
        if (!needsBook(player) || !player.connection.isAcceptingMessages()) return;
        if (!OPENED_BOOKS.add(player.getUUID())) return;
        List<String> configuredPages = new ArrayList<>();
        for (String page : Heos.getConfig().rulesPages.split("\\|", -1)) {
            configuredPages.add(page.replace("\\n", "\n"));
        }
        String prompt = Messages.text(player, "text.heos.rulesPrompt");
        String actionLabels = Heos.getConfig().requireRulesAgreement
                ? Messages.text(player, "text.heos.rulesAccept") + "\n" + Messages.text(player, "text.heos.rulesDecline")
                : Messages.text(player, "text.heos.rulesDone");
        String controlsText = prompt + "\n\n" + actionLabels;
        var buttons = Component.literal(prompt + "\n\n");
        if (Heos.getConfig().requireRulesAgreement) {
            buttons = buttons
                    .append(Component.literal(Messages.text(player, "text.heos.rulesAccept")).setStyle(Style.EMPTY.withBold(true).withClickEvent(runCommand("/rules agree"))))
                    .append(Component.literal("\n"))
                    .append(Component.literal(Messages.text(player, "text.heos.rulesDecline")).setStyle(Style.EMPTY.withBold(true).withClickEvent(runCommand("/rules decline"))));
        } else {
            buttons = buttons.append(Component.literal(Messages.text(player, "text.heos.rulesDone"))
                    .setStyle(Style.EMPTY.withBold(true).withClickEvent(runCommand("/rules done"))));
        }
        List<Component> pages = new ArrayList<>();
        int lastPageIndex = configuredPages.size() - 1;
        for (int index = 0; index < configuredPages.size(); index++) {
            String page = configuredPages.get(index);
            if (index == lastPageIndex && fitsOnPage(page + "\n\n" + controlsText)) {
                pages.add(Component.literal(page + "\n\n").append(buttons));
            } else {
                pages.add(Component.literal(page));
                if (index == lastPageIndex) pages.add(buttons);
            }
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        //? if >= 1.20.5 {
        List<Filterable<Component>> filteredPages = new ArrayList<>();
        for (Component page : pages) {
            filteredPages.add(Filterable.passThrough(page));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(Messages.text(player, "text.heos.rulesBookTitle")), "Heos", 0, filteredPages, true));
        //?} else {
        /*CompoundTag bookTag = book.getOrCreateTag();
        bookTag.putString("title", Messages.text(player, "text.heos.rulesBookTitle"));
        bookTag.putString("author", "Heos");
        ListTag bookPages = new ListTag();
        for (Component page : pages) {
            bookPages.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        }
        bookTag.put("pages", bookPages);
        *///?}

        // ClientboundOpenBookPacket only identifies a hand; the client reads the
        // book from its locally synchronized inventory. Temporarily synchronize
        // this book into the selected slot, then restore the original item.
        int selectedSlot = selectedSlot(player);
        ItemStack originalItem = player.getInventory().getItem(selectedSlot);
        player.getInventory().setItem(selectedSlot, book);
        player.inventoryMenu.broadcastChanges();
        player.openItemGui(book, InteractionHand.MAIN_HAND);
        player.level().getServer().execute(() -> {
            if (player.getInventory().getItem(selectedSlot) == book) {
                player.getInventory().setItem(selectedSlot, originalItem);
                player.inventoryMenu.broadcastChanges();
            }
        });
    }

    /** Reopens the rules book after a pending player attempts a restricted action. */
    public static void reopenBook(ServerPlayer player) {
        if (player != null) {
            OPENED_BOOKS.remove(player.getUUID());
        }
        openBook(player);
    }

    private static ClickEvent runCommand(String command) {
        //? if >= 1.21.5 {
        return new ClickEvent.RunCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
    }

    private static int selectedSlot(ServerPlayer player) {
        //? if >= 1.21.5 {
        return player.getInventory().getSelectedSlot();
        //?} else {
        /*return player.getInventory().selected;
        *///?}
    }

    /** Conservative server-side estimate; client font/layout is not exposed to the server. */
    private static boolean fitsOnPage(String text) {
        int lines = 1;
        int width = 0;
        for (int offset = 0; offset < text.length(); ) {
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

    public static int agree(ServerPlayer player) {
        if (player == null || !Heos.getConfig().enableRulesBook) return 0;
        PlayerData data = ((PlayerAuth) player).heos$getPlayerData();
        if (data == null || data.hasAcceptedRules) return 0;
        data.hasAcceptedRules = true;
        data.hasSeenRulesBook = true;
        data.save();
        OPENED_BOOKS.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(Messages.text(player, "text.heos.rulesAccepted")), false);
        ((PlayerAuth) player).heos$sendAuthMessage();
        return 1;
    }

    public static int decline(ServerPlayer player) {
        if (player == null || !Heos.getConfig().enableRulesBook) return 0;
        OPENED_BOOKS.remove(player.getUUID());
        player.connection.disconnect(Component.literal(Messages.text(player, "text.heos.rulesDeclined")));
        return 1;
    }

    public static int complete(ServerPlayer player) {
        if (player == null || !Heos.getConfig().enableRulesBook || Heos.getConfig().requireRulesAgreement) return 0;
        PlayerData data = ((PlayerAuth) player).heos$getPlayerData();
        if (data == null || data.hasSeenRulesBook || data.hasAcceptedRules) return 0;
        data.hasSeenRulesBook = true;
        data.save();
        OPENED_BOOKS.remove(player.getUUID());
        return 1;
    }
}
