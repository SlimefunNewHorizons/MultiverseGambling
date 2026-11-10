package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Card;
import com.chagui68.multiversegambling.engine.PokerBot;
import com.chagui68.multiversegambling.engine.PokerHand;
import com.chagui68.multiversegambling.engine.PokerTable;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.game.Ticking;
import com.chagui68.multiversegambling.gui.PokerBuyInGui;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.HoloButton;
import com.chagui68.multiversegambling.world.anim.PokerTableShow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Texas hold'em for up to eight players at a table in the casino world.
 *
 * <p>Unlike the other group games there is no betting window: players sit down with a
 * stack of chips (bought with money, or with items at their reference value) and hands
 * follow one another for as long as two or more players have chips. The rules live in
 * {@link PokerTable}; this class seats people, deals with the provably fair shuffle,
 * waits for each player in turn (checking or folding for them when their time runs out),
 * runs the house players and, when somebody stands up, turns their chips back into what
 * they bought them with.</p>
 *
 * <p>Money never lives only in memory: every change of the seats is written to
 * {@code poker-table.yml}, so a server that crashes mid hand gives everybody back the
 * stack they had when the hand started on the next start.</p>
 */
public final class PokerGame extends AbstractGame implements Ticking {

    public static final String ID = "poker";
    private static final String SAVE_FILE = "poker-table.yml";

    /** What the table is doing. */
    private enum Step {
        IDLE, DEAL, BET, COLLECT, STREET, SHOWDOWN
    }

    /**
     * Items given for chips, kept until the player stands up.
     */
    public record Escrow(ItemStack item, int count, long unitCents) {
        public long cents() {
            return unitCents * count;
        }
    }

    /** Somebody sitting at the table: a player or the house. */
    private static final class Sitter {
        private final UUID id;
        private final boolean bot;
        private final String name;
        private int seat;
        private final List<Escrow> items = new ArrayList<>();
        private final List<com.chagui68.multiversegambling.economy.Wager> buyIns = new ArrayList<>();
        private long pendingChips;
        private boolean leaving;
        private boolean away;
        private int timeouts;
        private int raiseIndex;
        private int lowerButtons;
        private long startStack;

        Sitter(UUID id, boolean bot, String name, int seat) {
            this.id = id;
            this.bot = bot;
            this.name = name;
            this.seat = seat;
        }
    }

    private final PokerTable table = new PokerTable(PokerTableShow.SEATS, 500, 1000);
    private final Map<UUID, Sitter> sitters = new LinkedHashMap<>();
    private final SplittableRandom botRandom = new SplittableRandom(Rng.generator().nextLong());
    private PokerTableShow show;
    private Step step = Step.IDLE;
    private int wait;
    private boolean countingDown;
    private int actor = -1;
    private int actorTicks;
    private int botWait;
    private int clock;
    private int quietTicks;
    private boolean unmounting;
    private List<Long> raiseLadder = List.of();

    public PokerGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder(ID, "Poker", GameCategory.GROUP, Material.PAPER)
                .desc("&7Texas hold'em for up to 8 players.",
                        "&7Sit at the table with your chips and",
                        "&7play hand after hand: call, raise, all in.")
                .players(2, PokerTableShow.SEATS)
                .build());
        refundCrashedTable();
    }

    // ================================================================ entry

    @Override
    public void open(Player player) {
        if (!enabled()) {
            message(player, "games.disabled", "game", name());
            return;
        }
        if (!player.hasPermission(permission())) {
            message(player, "general.no-permission");
            return;
        }
        Sitter sitter = sitters.get(player.getUniqueId());
        if (sitter != null) {
            if (show != null) {
                show.mount(sitter.seat, player);
            }
            message(player, "poker.already-seated", "seat", sitter.seat + 1);
            return;
        }
        int seat = table.freeSeat();
        if (seat < 0) {
            message(player, "poker.table-full");
            return;
        }
        ArenaStage stage = stage();
        if (stage != null && plugin.config().worldAnimationsTeleport()
                && !stage.arena().contains(player.getLocation().getBlockX(), player.getLocation().getBlockZ())) {
            stage.load();
            ensureShow();
            player.teleport(stage.watcher(14, ArenaStage.TABLE_PITCH));
        }
        new PokerBuyInGui(plugin, player, this, seat, false).show();
    }

    /**
     * Somebody clicked the "sit" button of a free chair.
     */
    private void requestSeat(Player player, int seat) {
        if (sitters.containsKey(player.getUniqueId())) {
            message(player, "poker.already-seated", "seat", sitters.get(player.getUniqueId()).seat + 1);
            return;
        }
        if (!enabled() || !player.hasPermission(permission())) {
            message(player, "general.no-permission");
            return;
        }
        new PokerBuyInGui(plugin, player, this, seat, false).show();
    }

    // ============================================================== limits

    /** Smallest buy-in, in hundredths of a coin. */
    public long minBuyIn() {
        return Math.max(bigBlindCents(), cents(minBet()));
    }

    /** Largest stack a player can sit with, in hundredths of a coin. */
    public long maxBuyIn() {
        return Math.max(minBuyIn(), cents(maxBet()));
    }

    public long bigBlindCents() {
        return Math.max(1, cents(plugin.config().pokerBigBlind()));
    }

    public long smallBlindCents() {
        return Math.max(1, Math.min(bigBlindCents(), cents(plugin.config().pokerSmallBlind())));
    }

    /** Chips the player has at the table, waiting chips included, or -1 when not seated. */
    public long stackOf(UUID id) {
        Sitter sitter = sitters.get(id);
        if (sitter == null || table.seat(sitter.seat) == null) {
            return -1;
        }
        return table.seat(sitter.seat).stack() + sitter.pendingChips;
    }

    public boolean itemBuyIn() {
        return plugin.config().pokerItemBuyIn();
    }

    public static long cents(double coins) {
        return Math.round(coins * 100.0);
    }

    public String format(long cents) {
        return plugin.economy().format(cents / 100.0);
    }

    // ============================================================= seating

    /**
     * Sits a player down with chips already paid for: {@code moneyCents} were taken from
     * their balance and {@code items} were taken from their inventory. When the seat
     * cannot be taken everything is given back.
     *
     * @return true when the player is now at the table
     */
    public boolean sitDown(Player player, int wanted, long moneyCents, List<Escrow> items,
                           com.chagui68.multiversegambling.economy.Wager buyIn) {
        long itemCents = items.stream().mapToLong(Escrow::cents).sum();
        long chips = moneyCents + itemCents;
        UUID id = player.getUniqueId();
        if (sitters.containsKey(id)) {
            // Already seated: the purchase is a top up.
            return topUp(player, moneyCents, items, buyIn);
        }
        int seat = table.seat(wanted) == null ? wanted : table.freeSeat();
        if (seat < 0 || chips <= 0) {
            giveBack(id, moneyCents, items, buyIn);
            message(player, "poker.table-full");
            return false;
        }
        if (!table.sit(seat, id, chips)) {
            giveBack(id, moneyCents, items, buyIn);
            message(player, "poker.table-full");
            return false;
        }
        Sitter sitter = new Sitter(id, false, player.getName(), seat);
        sitter.items.addAll(items);
        if (buyIn != null) {
            sitter.buyIns.add(buyIn);
        }
        sitters.put(id, sitter);
        // The house only fills in for an empty table: a second player sends it away.
        if (humans() >= 2 && !table.handRunning()) {
            removeBots();
        }
        if (ensureShow()) {
            show.sitButton(seat, null, null);
            unmounting = true;
            show.mount(seat, player);
            unmounting = false;
        }
        message(player, "poker.seated", "seat", seat + 1, "chips", format(chips),
                "small", format(smallBlindCents()), "big", format(bigBlindCents()));
        tellTableExcept(id, "poker.joined", "player", player.getName(), "seat", seat + 1);
        if (humans() == 1 && bots() == 0) {
            offerHouse(player);
        }
        refreshSeat(seat);
        refreshUtility(sitter);
        saveState();
        return true;
    }

    private boolean topUp(Player player, long moneyCents, List<Escrow> items,
                          com.chagui68.multiversegambling.economy.Wager buyIn) {
        Sitter sitter = sitters.get(player.getUniqueId());
        long chips = moneyCents + items.stream().mapToLong(Escrow::cents).sum();
        long room = maxBuyIn() - stackOf(sitter.id);
        if (chips <= 0 || chips > room) {
            giveBack(sitter.id, moneyCents, items, buyIn);
            message(player, "poker.top-up-too-much", "max", format(Math.max(0, room)));
            return false;
        }
        sitter.items.addAll(items);
        if (buyIn != null) {
            sitter.buyIns.add(buyIn);
        }
        PokerTable.Seat seat = table.seat(sitter.seat);
        if (table.handRunning() && seat.inHand()) {
            sitter.pendingChips += chips;
            message(player, "poker.top-up-later", "chips", format(chips));
        } else {
            table.addChips(sitter.seat, chips);
            message(player, "poker.top-up", "chips", format(chips));
        }
        refreshSeat(sitter.seat);
        saveState();
        return true;
    }

    /**
     * A player asked to stand up: right away between hands, at the end of the hand when
     * they are in it (folding first when it is their turn).
     */
    public void standUp(UUID id, boolean quietly) {
        Sitter sitter = sitters.get(id);
        if (sitter == null || sitter.bot) {
            return;
        }
        sitter.leaving = true;
        PokerTable.Seat seat = table.seat(sitter.seat);
        if (table.handRunning() && seat != null && seat.inHand()) {
            Player player = online(id);
            if (player != null && !quietly) {
                message(player, "poker.leaving-after-hand");
            }
            if (table.toAct() == sitter.seat) {
                autoAct(sitter);
            }
            refreshSeat(sitter.seat);
            return;
        }
        leave(sitter);
    }

    /**
     * Takes a sitter away from the table and pays out their chips.
     */
    private void leave(Sitter sitter) {
        if (sitters.get(sitter.id) != sitter) {
            return;
        }
        long chips = table.leave(sitter.seat);
        if (chips < 0) {
            sitter.leaving = true;
            return;
        }
        sitters.remove(sitter.id);
        int seat = sitter.seat;
        if (show != null) {
            show.clearControls(seat);
            show.utility(seat, null, List.of());
            unmounting = true;
            show.unmount(seat);
            unmounting = false;
        }
        if (!sitter.bot) {
            cashOut(sitter, chips + sitter.pendingChips, true);
        }
        refreshSeat(seat);
        if (humans() == 0) {
            removeBots();
        } else if (humans() == 1 && bots() == 0) {
            Player last = onlineHuman();
            if (last != null) {
                offerHouse(last);
            }
        }
        saveState();
    }

    /**
     * Turns chips back into what they were bought with: the items first, the most
     * valuable ones first, while the chips cover them, and the rest as money. A player
     * who won gets every item back plus the winnings; one who lost gets back what their
     * remaining chips are still worth.
     */
    /** What a pile of chips turns into: the items it can buy back first, the rest as money. */
    private record Plan(long left, List<ItemStack> back, int itemsBack) {
    }

    private static Plan plan(Sitter sitter, long chips) {
        long left = Math.max(0, chips);
        List<Escrow> sorted = new ArrayList<>(sitter.items);
        sorted.sort(Comparator.comparingLong(Escrow::unitCents).reversed());
        List<ItemStack> back = new ArrayList<>();
        int itemsBack = 0;
        for (Escrow escrow : sorted) {
            int units = (int) Math.min(escrow.count(), escrow.unitCents() <= 0 ? 0 : left / escrow.unitCents());
            if (units <= 0) {
                continue;
            }
            left -= units * escrow.unitCents();
            itemsBack += units;
            int max = Math.max(1, escrow.item().getMaxStackSize());
            for (int given = 0; given < units; given += max) {
                back.add(escrow.item().asQuantity(Math.min(max, units - given)));
            }
        }
        return new Plan(left, back, itemsBack);
    }

    /**
     * @param payMoney false after a crash: the bank part is already owed by the bet journal
     *                 (see {@link #saveState}) and the casino pays it from there
     */
    private void cashOut(Sitter sitter, long chips, boolean payMoney) {
        Plan plan = plan(sitter, chips);
        long left = plan.left();
        if (payMoney) {
            if (sitter.buyIns.isEmpty()) {
                if (left > 0) {
                    plugin.economy().deposit(sitter.id, left / 100.0);
                }
            } else {
                // The first buy-in carries the whole payout; the others are absorbed by it.
                sitter.buyIns.get(0).payAbsolute(left / 100.0);
                for (int i = 1; i < sitter.buyIns.size(); i++) {
                    sitter.buyIns.get(i).lose();
                }
                sitter.buyIns.clear();
            }
        }
        if (!plan.back().isEmpty()) {
            plugin.items().giveStacks(sitter.id, plan.back());
        }
        int itemsBack = plan.itemsBack();
        sitter.items.clear();
        Player player = online(sitter.id);
        if (player != null) {
            if (itemsBack > 0) {
                message(player, "poker.cashed-out-items", "money", format(left), "items", itemsBack);
            } else {
                message(player, "poker.cashed-out", "money", format(left));
            }
        }
    }

    private void giveBack(UUID id, long moneyCents, List<Escrow> items,
                          com.chagui68.multiversegambling.economy.Wager buyIn) {
        if (buyIn != null) {
            buyIn.refund();
        } else if (moneyCents > 0) {
            plugin.economy().deposit(id, moneyCents / 100.0);
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Escrow escrow : items) {
            int max = Math.max(1, escrow.item().getMaxStackSize());
            for (int given = 0; given < escrow.count(); given += max) {
                stacks.add(escrow.item().asQuantity(Math.min(max, escrow.count() - given)));
            }
        }
        plugin.items().giveStacks(id, stacks);
    }

    // ================================================================ house

    private void offerHouse(Player player) {
        player.sendMessage(plugin.messages().componentPlainFor(player, "poker.house-offer")
                .append(Text.c(" "))
                .append(Text.button(label(player, "replay.house"), "/mvgam action house",
                        label(player, "poker.house-hover"))));
    }

    private void seatHouse(Player player) {
        if (!sitters.containsKey(player.getUniqueId())) {
            message(player, "group.not-in-table");
            return;
        }
        if (humans() != 1 || bots() > 0 || table.handRunning()) {
            message(player, "poker.house-busy");
            return;
        }
        long stack = Math.max(minBuyIn(), Math.min(maxBuyIn(), stackOf(player.getUniqueId())));
        int wanted = plugin.config().pokerHouseBots();
        for (int i = 0; i < wanted; i++) {
            int seat = table.freeSeat();
            if (seat < 0) {
                break;
            }
            UUID botId = new UUID(0x484F555345L, i + 1L);
            String name = houseName() + (wanted > 1 ? " " + (i + 1) : "");
            if (table.sit(seat, botId, stack)) {
                sitters.put(botId, new Sitter(botId, true, name, seat));
                if (show != null) {
                    show.sitButton(seat, null, null);
                }
                refreshSeat(seat);
            }
        }
        message(player, "poker.house-seated", "rake", Text.percent(plugin.config().pokerHouseRake()));
        refreshUtility(sitters.get(player.getUniqueId()));
        if (show != null) {
            show.say(plugin.messages().get("poker.dealer.house"));
        }
    }

    private void removeBots() {
        for (Sitter sitter : new ArrayList<>(sitters.values())) {
            if (sitter.bot) {
                PokerTable.Seat seat = table.seat(sitter.seat);
                if (seat == null || !table.handRunning() || !seat.inHand()) {
                    leave(sitter);
                }
            }
        }
    }

    private String houseName() {
        return Text.strip(plugin.messages().getOr("poker.house-name", "The House"));
    }

    // ================================================================ clock

    @Override
    public void tick() {
        clock++;
        if (clock % 20 == 0) {
            maintainShow();
            everySecond();
        }
        switch (step) {
            case IDLE -> idle();
            case DEAL, STREET -> {
                if (--wait <= 0) {
                    step = Step.BET;
                }
            }
            case BET -> betting();
            case COLLECT -> {
                if (--wait <= 0) {
                    afterCollect();
                }
            }
            case SHOWDOWN -> {
                if (--wait <= 0) {
                    endHand();
                }
            }
        }
    }

    private void idle() {
        if (table.ready() < 2) {
            countingDown = false;
            return;
        }
        if (!countingDown) {
            countingDown = true;
            wait = plugin.config().pokerNextHandSeconds() * 20;
            tellTable("poker.next-hand", "seconds", wait / 20);
            return;
        }
        if (--wait <= 0) {
            countingDown = false;
            startHand();
        }
    }

    private void startHand() {
        table.blinds(smallBlindCents(), bigBlindCents());
        double rake = bots() > 0 ? plugin.config().pokerHouseRake() : plugin.config().pokerRake();
        table.rake(rake, cents(plugin.config().pokerRakeCap()));
        for (Sitter sitter : sitters.values()) {
            sitter.raiseIndex = 0;
            PokerTable.Seat seat = table.seat(sitter.seat);
            sitter.startStack = seat == null ? 0 : seat.stack();
            table.sitOut(sitter.seat, sitter.leaving || sitter.away);
        }
        if (table.ready() < 2) {
            return;
        }
        double[] rolls = plugin.fair().rolls(FairnessService.HOUSE, Card.Deck.rollsFor(1));
        if (!table.startHand(Card.Deck.shuffled(1, rolls))) {
            return;
        }
        step = Step.DEAL;
        actor = -1;
        saveState();
        int dealt = 0;
        if (show != null) {
            show.clearHand();
            show.dealerButton(table.button());
            show.say(plugin.messages().get("poker.dealer.shuffle", "hand", table.hands()));
            int seat = table.smallBlindSeat();
            for (int i = 0; i < table.size(); i++) {
                int index = (seat + i) % table.size();
                PokerTable.Seat at = table.seat(index);
                if (at == null || !at.inHand()) {
                    continue;
                }
                Sitter sitter = bySeat(index);
                show.dealHole(index, sitter == null || sitter.bot ? null : sitter.id, at.hole(), 4 + dealt * 4);
                dealt++;
            }
            showBet(table.smallBlindSeat());
            showBet(table.bigBlindSeat());
            show.pot(0, bigBlindCents(), "");
        }
        wait = 20 + dealt * 4 + 14;
        for (Sitter sitter : sitters.values()) {
            PokerTable.Seat seat = table.seat(sitter.seat);
            Player player = online(sitter.id);
            if (player == null || seat == null || !seat.inHand()) {
                continue;
            }
            tellKeyed(player, "poker.hand-start", "hand", table.hands(),
                    "small", format(table.smallBlind()), "big", format(table.bigBlind()));
            tellKeyed(player, "poker.your-cards", "cards", PokerTableShow.cardsText(seat.hole()));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        }
        refreshAllSeats();
    }

    private void betting() {
        if (table.phase() == PokerTable.Phase.FINISHED) {
            showdown();
            return;
        }
        if (table.phase() == PokerTable.Phase.STREET_DONE) {
            collect();
            return;
        }
        if (table.phase() != PokerTable.Phase.BETTING) {
            step = Step.IDLE;
            return;
        }
        int seat = table.toAct();
        Sitter sitter = bySeat(seat);
        if (sitter == null) {
            table.act(seat, PokerTable.Action.FOLD, 0);
            afterAction(seat, PokerTable.Action.FOLD, 0, false);
            return;
        }
        if (seat != actor) {
            actor = seat;
            newActor(sitter);
            return;
        }
        if (sitter.bot) {
            if (--botWait <= 0) {
                PokerBot.Decision decision = PokerBot.decide(table, seat, botRandom);
                act(sitter, decision.action(), decision.raiseTo(), false);
            }
            return;
        }
        if (sitter.away || sitter.leaving || online(sitter.id) == null) {
            autoAct(sitter);
            return;
        }
        actorTicks--;
        if (actorTicks <= 0) {
            timeout(sitter);
        } else if (actorTicks == 200) {
            Player player = online(sitter.id);
            if (player != null) {
                tellKeyed(player, "poker.hurry", "seconds", 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
            }
        }
    }

    private void newActor(Sitter sitter) {
        if (sitter.bot) {
            botWait = 25 + botRandom.nextInt(30);
            refreshSeat(sitter.seat);
            return;
        }
        actorTicks = plugin.config().pokerActionSeconds() * 20;
        PokerTable.Options options = table.options(sitter.seat);
        raiseLadder = ladder(options);
        sitter.raiseIndex = Math.max(0, Math.min(sitter.raiseIndex, raiseLadder.size() - 1));
        showControls(sitter);
        Player player = online(sitter.id);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.4f);
            showTitle(player, "poker.turn-title", "poker.turn-subtitle",
                    "call", options.toCall() > 0 ? format(options.callAmount()) : label(player, "poker.nothing"));
            sendChatControls(player, options);
        }
        refreshSeat(sitter.seat);
    }

    private void timeout(Sitter sitter) {
        sitter.timeouts++;
        Player player = online(sitter.id);
        if (player != null) {
            tellKeyed(player, "poker.timed-out");
        }
        if (sitter.timeouts >= 2) {
            // Twice in a row away from the keyboard: they get up after this hand.
            sitter.leaving = true;
            if (player != null) {
                tellKeyed(player, "poker.sat-out");
            }
        }
        autoAct(sitter);
    }

    /** Checks when it costs nothing, folds otherwise. */
    private void autoAct(Sitter sitter) {
        PokerTable.Options options = table.options(sitter.seat);
        if (options == null) {
            return;
        }
        act(sitter, options.canCheck() ? PokerTable.Action.CHECK : PokerTable.Action.FOLD, 0, false);
    }

    /**
     * A decision of the player in that seat. Every path (holograms, chat, bots,
     * timeouts) ends here, and the table refuses anything that is not legal.
     */
    private void act(Sitter sitter, PokerTable.Action action, long raiseTo, boolean manual) {
        int seat = sitter.seat;
        if (table.toAct() != seat || step != Step.BET) {
            Player player = online(sitter.id);
            if (player != null && !sitter.bot) {
                tellKeyed(player, "poker.not-your-turn");
            }
            return;
        }
        long before = table.seat(seat).bet();
        boolean opening = table.currentBet() == 0;
        if (!table.act(seat, action, raiseTo)) {
            Player player = online(sitter.id);
            if (player != null && !sitter.bot) {
                tellKeyed(player, "poker.illegal");
            }
            return;
        }
        long put = table.seat(seat).bet() - before;
        if (manual) {
            sitter.timeouts = 0;
        }
        afterAction(seat, action, action == PokerTable.Action.CALL ? put
                : action == PokerTable.Action.CHECK || action == PokerTable.Action.FOLD ? 0 : table.seat(seat).bet(),
                opening);
    }

    private void afterAction(int seat, PokerTable.Action action, long amount, boolean opening) {
        actor = -1;
        Sitter sitter = bySeat(seat);
        String name = sitter == null ? "?" : sitter.name;
        if (show != null) {
            show.clearControls(seat);
            if (action == PokerTable.Action.FOLD) {
                show.fold(seat);
            } else {
                showBet(seat);
            }
        }
        PokerTable.Seat at = table.seat(seat);
        String key = switch (action) {
            case FOLD -> "poker.action.fold";
            case CHECK -> "poker.action.check";
            case CALL -> at != null && at.allIn() ? "poker.action.call-all-in" : "poker.action.call";
            case RAISE -> opening ? "poker.action.bet" : "poker.action.raise";
            case ALL_IN -> "poker.action.all-in";
        };
        tellTable(key, "player", name, "amount", format(amount));
        if (show != null) {
            show.say(plugin.messages().get(key, "player", name, "amount", format(amount)));
        }
        soundTable(action == PokerTable.Action.FOLD ? Sound.ITEM_BOOK_PAGE_TURN
                : action == PokerTable.Action.CHECK ? Sound.BLOCK_WOODEN_BUTTON_CLICK_ON : Sound.BLOCK_CHAIN_PLACE);
        refreshSeat(seat);
    }

    private void collect() {
        clearAllControls();
        if (show != null) {
            show.collect(table.pot(), bigBlindCents(), potText(table.pot()));
        }
        step = Step.COLLECT;
        wait = show == null ? 2 : 12;
    }

    private void afterCollect() {
        int before = table.board().size();
        table.nextStreet();
        if (table.phase() == PokerTable.Phase.FINISHED) {
            showdown();
            return;
        }
        int dealtNow = table.board().size() - before;
        if (dealtNow > 0) {
            String street = switch (table.street()) {
                case FLOP -> "poker.street.flop";
                case TURN -> "poker.street.turn";
                default -> "poker.street.river";
            };
            if (show != null) {
                show.board(table.board(), 2);
                show.say(plugin.messages().get(street) + " &f" + PokerTableShow.cardsText(table.board()));
            }
            for (Sitter sitter : sitters.values()) {
                Player player = online(sitter.id);
                if (player != null) {
                    tellKeyed(player, "poker.board", "street", label(player, street),
                            "cards", PokerTableShow.cardsText(table.board()));
                }
            }
            soundTable(Sound.ITEM_BOOK_PAGE_TURN);
        }
        step = Step.STREET;
        wait = show == null ? 10 : (table.runningOut() ? 40 : 16 + dealtNow * 5);
        refreshAllSeats();
    }

    private void showdown() {
        clearAllControls();
        step = Step.SHOWDOWN;
        List<PokerTable.Award> awards = table.results();
        Set<Integer> winners = new LinkedHashSet<>();
        Map<Integer, Long> won = new LinkedHashMap<>();
        for (PokerTable.Award award : awards) {
            for (int i = 0; i < award.winners().size(); i++) {
                winners.add(award.winners().get(i));
                won.merge(award.winners().get(i), award.shares().get(i), Long::sum);
            }
        }
        if (show != null) {
            if (!table.uncontested()) {
                for (int i = 0; i < table.size(); i++) {
                    PokerTable.Seat seat = table.seat(i);
                    if (seat != null && seat.live() && seat.hole().size() == 2) {
                        PokerHand.Result hand = PokerHand.best(seat.hole(), table.board());
                        show.reveal(i, seat.hole(), handName(null, hand.category()), winners.contains(i));
                    }
                }
                if (!awards.isEmpty() && awards.get(0).hand() != null) {
                    show.glowBoard(awards.get(0).hand().best(), table.board());
                }
            }
            long total = awards.stream().mapToLong(PokerTable.Award::amount).sum();
            show.collect(total, bigBlindCents(), potText(total));
            List<Integer> pushed = new ArrayList<>(winners);
            List<Integer> humanWinners = new ArrayList<>();
            for (int seat : winners) {
                Sitter sitter = bySeat(seat);
                if (sitter != null && !sitter.bot) {
                    humanWinners.add(seat);
                }
            }
            PokerTableShow current = show;
            // The bets reach the pot first, then the pot slides to whoever won it.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (current == show && show != null) {
                    show.push(pushed);
                    show.celebrate(humanWinners);
                }
            }, 14L);
        }
        // The results, side pots included, for everybody at the table.
        for (int index = 0; index < awards.size(); index++) {
            PokerTable.Award award = awards.get(index);
            String pot = index == 0 ? "poker.pot.main" : "poker.pot.side";
            for (int i = 0; i < award.winners().size(); i++) {
                Sitter sitter = bySeat(award.winners().get(i));
                String name = sitter == null ? "?" : sitter.name;
                long share = award.shares().get(i);
                if (award.hand() == null) {
                    tellTable("poker.wins-uncontested", "player", name, "amount", format(share));
                    if (show != null) {
                        show.say(plugin.messages().get("poker.wins-uncontested", "player", name,
                                "amount", format(share)));
                    }
                } else {
                    for (Sitter reader : sitters.values()) {
                        Player player = online(reader.id);
                        if (player != null) {
                            tellKeyed(player, "poker.wins", "player", name, "amount", format(share),
                                    "pot", label(player, pot), "hand", handName(player, award.hand().category()),
                                    "cards", PokerTableShow.cardsText(award.hand().best()));
                        }
                    }
                    if (show != null) {
                        show.say(plugin.messages().get("poker.wins-short", "player", name, "amount", format(share),
                                "hand", handName(null, award.hand().category())));
                    }
                }
            }
        }
        // Statistics and big win announcements, for the players.
        for (Sitter sitter : sitters.values()) {
            PokerTable.Seat seat = table.seat(sitter.seat);
            if (sitter.bot || seat == null || !seat.inHand()) {
                continue;
            }
            long gain = won.getOrDefault(sitter.seat, 0L);
            long wagered = sitter.startStack - (seat.stack() - gain);
            if (wagered <= 0 && gain <= 0) {
                continue;
            }
            plugin.stats().record(sitter.id, id(), Math.max(0, wagered) / 100.0, gain / 100.0);
            Player player = online(sitter.id);
            if (player != null && gain > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
                plugin.games().announceWin(player, Math.max(0, wagered) / 100.0, gain / 100.0);
            }
        }
        wait = table.uncontested() ? 60 : 130;
        refreshAllSeats();
        saveState();
    }

    private void endHand() {
        if (show != null) {
            show.clearHand();
        }
        step = Step.IDLE;
        actor = -1;
        for (Sitter sitter : new ArrayList<>(sitters.values())) {
            PokerTable.Seat seat = table.seat(sitter.seat);
            if (seat == null) {
                continue;
            }
            if (sitter.pendingChips > 0) {
                table.addChips(sitter.seat, sitter.pendingChips);
                sitter.pendingChips = 0;
            }
            if (seat.stack() <= 0 && !sitter.bot) {
                Player player = online(sitter.id);
                if (player != null) {
                    tellKeyed(player, "poker.busted");
                    player.sendMessage(plugin.messages().componentPlainFor(player, "poker.sit-again-prompt")
                            .append(Text.c(" "))
                            .append(Text.button(label(player, "poker.sit-again"), "/mvgam play poker",
                                    label(player, "poker.sit-again-hover"))));
                }
                leave(sitter);
            } else if (sitter.leaving || sitter.away || (!sitter.bot && online(sitter.id) == null)) {
                leave(sitter);
            } else if (sitter.bot && seat.stack() <= 0) {
                leave(sitter);
            }
        }
        if (humans() == 0 || humans() >= 2) {
            removeBots();
        }
        refreshAllSeats();
        for (Sitter sitter : sitters.values()) {
            refreshUtility(sitter);
        }
        saveState();
    }

    // ============================================================ controls

    /**
     * The raise sizes offered: the minimum, then fractions of the pot, all below the
     * all in (which has its own button).
     */
    private List<Long> ladder(PokerTable.Options options) {
        List<Long> sizes = new ArrayList<>();
        if (options == null || !options.canRaise()) {
            return sizes;
        }
        long min = options.minRaiseTo();
        long max = options.maxRaiseTo();
        if (min < max) {
            sizes.add(min);
        }
        long pot = table.pot() + options.toCall();
        for (double fraction : new double[]{0.33, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0}) {
            long to = table.currentBet() + Math.round(pot * fraction);
            // Whole small blinds read better than odd cents.
            long unit = Math.max(1, smallBlindCents());
            to = Math.round(to / (double) unit) * unit;
            if (to > min && to < max && (sizes.isEmpty() || to > sizes.get(sizes.size() - 1))) {
                sizes.add(to);
            }
        }
        return sizes;
    }

    private void showControls(Sitter sitter) {
        Player player = online(sitter.id);
        PokerTable.Options options = table.options(sitter.seat);
        if (show == null || player == null || options == null) {
            return;
        }
        List<HoloButton.Spec> lower = new ArrayList<>();
        lower.add(new HoloButton.Spec(Text.c(label(player, "poker.button.fold")), HoloButton.RED, 1.0f,
                p -> handleAction(p, "fold", new String[0])));
        if (options.canCheck()) {
            lower.add(new HoloButton.Spec(Text.c(label(player, "poker.button.check")), HoloButton.BLUE, 1.0f,
                    p -> handleAction(p, "check", new String[0])));
        } else {
            lower.add(new HoloButton.Spec(Text.c(label(player, "poker.button.call", "amount",
                    format(options.callAmount()))), HoloButton.GREEN, 1.0f,
                    p -> handleAction(p, "call", new String[0])));
        }
        if (options.canAllIn()) {
            lower.add(new HoloButton.Spec(Text.c(label(player, "poker.button.all-in", "amount",
                    format(table.seat(sitter.seat).stack()))), HoloButton.PURPLE, 1.0f,
                    p -> handleAction(p, "allin", new String[0])));
        }
        List<HoloButton.Spec> upper = new ArrayList<>();
        if (!raiseLadder.isEmpty()) {
            upper.add(new HoloButton.Spec(Text.c(label(player, "poker.button.less")), HoloButton.GREY, 0.9f,
                    p -> handleAction(p, "less", new String[0])));
            upper.add(new HoloButton.Spec(raiseText(player, options, true), HoloButton.GOLD, 1.0f,
                    p -> handleAction(p, "raise", new String[0])));
            upper.add(new HoloButton.Spec(Text.c(label(player, "poker.button.more")), HoloButton.GREY, 0.9f,
                    p -> handleAction(p, "more", new String[0])));
        }
        show.controls(sitter.seat, sitter.id, lower, upper);
        sitter.lowerButtons = lower.size();
    }

    /**
     * Text of the raise button. Built once with the largest size so its hitbox fits any
     * size the player picks later.
     */
    private Component raiseText(Player player, PokerTable.Options options, boolean widest) {
        Sitter sitter = sitters.get(player.getUniqueId());
        long size = raiseLadder.isEmpty() ? options.minRaiseTo()
                : raiseLadder.get(widest ? raiseLadder.size() - 1 : Math.min(sitter.raiseIndex, raiseLadder.size() - 1));
        String key = options.betting() ? "poker.button.bet" : "poker.button.raise";
        Component text = Text.c(label(player, key, "amount", format(size)));
        if (widest && show != null) {
            // The real label replaces the widest one right after the button is made.
            plugin.getServer().getScheduler().runTask(plugin, () -> relabelRaise(player));
        }
        return text;
    }

    private void relabelRaise(Player player) {
        Sitter sitter = sitters.get(player.getUniqueId());
        PokerTable.Options options = sitter == null ? null : table.options(sitter.seat);
        if (sitter == null || options == null || show == null || raiseLadder.isEmpty()) {
            return;
        }
        show.relabel(sitter.seat, sitter.lowerButtons + 1, raiseText(player, options, false));
    }

    private void sendChatControls(Player player, PokerTable.Options options) {
        Component line = plugin.messages().componentPlainFor(player, "poker.chat-turn").append(Text.c(" "));
        line = line.append(Text.button(label(player, "poker.chat.fold"), "/mvgam action fold",
                label(player, "poker.chat.fold-hover"))).append(Text.c(" "));
        if (options.canCheck()) {
            line = line.append(Text.button(label(player, "poker.chat.check"), "/mvgam action check",
                    label(player, "poker.chat.check-hover")));
        } else {
            line = line.append(Text.button(label(player, "poker.chat.call", "amount", format(options.callAmount())),
                    "/mvgam action call", label(player, "poker.chat.call-hover")));
        }
        for (int i = 0; i < raiseLadder.size(); i++) {
            if (i != 0 && i != raiseLadder.size() / 2 && i != raiseLadder.size() - 1) {
                continue;
            }
            long size = raiseLadder.get(i);
            line = line.append(Text.c(" ")).append(Text.button(
                    label(player, "poker.chat.raise", "amount", format(size)),
                    "/mvgam action raise " + String.format(java.util.Locale.ROOT, "%.2f", size / 100.0),
                    label(player, "poker.chat.raise-hover")));
        }
        if (options.canAllIn()) {
            line = line.append(Text.c(" ")).append(Text.button(label(player, "poker.chat.all-in"),
                    "/mvgam action allin", label(player, "poker.chat.all-in-hover")));
        }
        player.sendMessage(line);
    }

    private void clearAllControls() {
        if (show == null) {
            return;
        }
        for (int seat = 0; seat < table.size(); seat++) {
            show.clearControls(seat);
        }
    }

    /**
     * The small buttons a seated player always has: stand up, add chips and, alone at
     * the table, call the house in.
     */
    private void refreshUtility(Sitter sitter) {
        Player player = online(sitter.id);
        if (show == null || sitter.bot || player == null) {
            return;
        }
        List<HoloButton.Spec> specs = new ArrayList<>();
        specs.add(new HoloButton.Spec(Text.c(label(player, "poker.button.leave")), HoloButton.GREY, 0.75f,
                p -> handleAction(p, "leave", new String[0])));
        specs.add(new HoloButton.Spec(Text.c(label(player, "poker.button.chips")), HoloButton.GREEN, 0.75f,
                p -> handleAction(p, "chips", new String[0])));
        if (humans() == 1 && bots() == 0) {
            specs.add(new HoloButton.Spec(Text.c(label(player, "poker.button.house")), HoloButton.GOLD, 0.75f,
                    p -> handleAction(p, "house", new String[0])));
        }
        show.utility(sitter.seat, sitter.id, specs);
    }

    // ============================================================ actions

    @Override
    public void handleAction(Player player, String action, String[] args) {
        Sitter sitter = sitters.get(player.getUniqueId());
        if (sitter == null) {
            message(player, "group.not-in-table");
            return;
        }
        switch (action) {
            case "fold" -> act(sitter, PokerTable.Action.FOLD, 0, true);
            case "check" -> act(sitter, PokerTable.Action.CHECK, 0, true);
            case "call" -> act(sitter, PokerTable.Action.CALL, 0, true);
            case "allin", "all-in" -> act(sitter, PokerTable.Action.ALL_IN, 0, true);
            case "raise", "bet" -> {
                if (table.toAct() != sitter.seat) {
                    tellKeyed(player, "poker.not-your-turn");
                    return;
                }
                long to;
                if (args.length > 0) {
                    try {
                        to = cents(Double.parseDouble(args[0].replace(',', '.')));
                    } catch (NumberFormatException error) {
                        tellKeyed(player, "poker.illegal");
                        return;
                    }
                } else if (!raiseLadder.isEmpty()) {
                    to = raiseLadder.get(Math.min(sitter.raiseIndex, raiseLadder.size() - 1));
                } else {
                    PokerTable.Options options = table.options(sitter.seat);
                    to = options == null ? 0 : options.minRaiseTo();
                }
                PokerTable.Options options = table.options(sitter.seat);
                if (options != null && to >= options.maxRaiseTo() && options.canAllIn()) {
                    act(sitter, PokerTable.Action.ALL_IN, 0, true);
                } else {
                    act(sitter, PokerTable.Action.RAISE, to, true);
                }
            }
            case "less", "more" -> {
                if (table.toAct() != sitter.seat || raiseLadder.isEmpty()) {
                    return;
                }
                int step = "less".equals(action) ? -1 : 1;
                sitter.raiseIndex = Math.max(0, Math.min(raiseLadder.size() - 1, sitter.raiseIndex + step));
                relabelRaise(player);
            }
            case "leave", "stand" -> standUp(player.getUniqueId(), false);
            case "chips", "rebuy" -> {
                if (stackOf(player.getUniqueId()) >= maxBuyIn()) {
                    message(player, "poker.top-up-too-much", "max", format(0));
                    return;
                }
                new PokerBuyInGui(plugin, player, this, sitter.seat, true).show();
            }
            case "house" -> seatHouse(player);
            default -> message(player, "group.unknown-action");
        }
    }

    @Override
    public boolean ownsPlayer(UUID playerId) {
        Sitter sitter = sitters.get(playerId);
        return sitter != null && !sitter.bot;
    }

    @Override
    public void handleQuit(UUID playerId) {
        Sitter sitter = sitters.get(playerId);
        if (sitter == null || sitter.bot) {
            return;
        }
        sitter.away = true;
        standUp(playerId, true);
    }

    /**
     * A player got off a chair by themselves (sneaking, a teleport...): they stand up.
     */
    public void handleDismount(Player player, Entity vehicle) {
        if (unmounting || show == null || show.seatOfVehicle(vehicle) < 0) {
            return;
        }
        Sitter sitter = sitters.get(player.getUniqueId());
        if (sitter != null && !sitter.bot && !sitter.leaving) {
            standUp(player.getUniqueId(), false);
        }
    }

    // =============================================================== show

    private ArenaStage stage() {
        if (plugin.world() == null || !plugin.config().worldAnimationsEnabled()) {
            return null;
        }
        return plugin.world().stage(id());
    }

    /**
     * Makes sure the table stands in its pavilion.
     *
     * @return true when there is a table to sit at
     */
    private boolean ensureShow() {
        if (show != null && show.intact()) {
            return true;
        }
        ArenaStage stage = stage();
        if (stage == null) {
            return false;
        }
        if (show != null) {
            show.cancel();
        }
        show = new PokerTableShow(plugin, stage);
        show.start();
        // Whoever is already seated sits back down on the new chairs.
        unmounting = true;
        for (Sitter sitter : sitters.values()) {
            show.sitButton(sitter.seat, null, null);
            Player player = online(sitter.id);
            if (player != null && !sitter.bot) {
                show.mount(sitter.seat, player);
            }
        }
        unmounting = false;
        refreshAllSeats();
        for (Sitter sitter : sitters.values()) {
            refreshUtility(sitter);
        }
        if (table.handRunning()) {
            // A table rebuilt mid hand shows the hand as it stands.
            for (int i = 0; i < table.size(); i++) {
                PokerTable.Seat seat = table.seat(i);
                if (seat != null && seat.live()) {
                    Sitter sitter = bySeat(i);
                    show.dealHole(i, sitter == null || sitter.bot ? null : sitter.id, seat.hole(), 0);
                    showBet(i);
                }
            }
            show.board(table.board(), 0);
            show.dealerButton(table.button());
            actor = -1;
        }
        return true;
    }

    /**
     * Puts the table up when somebody comes near and takes it down when the pavilion
     * has been empty for a while.
     */
    private void maintainShow() {
        ArenaStage stage = stage();
        if (stage == null) {
            return;
        }
        boolean audience = !stage.nearbyPlayers(48).isEmpty();
        if (humans() > 0 || audience) {
            quietTicks = 0;
            if (show == null || !show.intact()) {
                ensureShow();
            }
            return;
        }
        quietTicks += 20;
        if (show != null && quietTicks >= 600) {
            show.cancel();
            show = null;
        }
    }

    private void everySecond() {
        if (show != null && (actor >= 0 || step == Step.IDLE)) {
            refreshSeat(actor);
        }
        // Each seated player keeps their hand and stack in the action bar.
        for (Sitter sitter : sitters.values()) {
            Player player = online(sitter.id);
            PokerTable.Seat seat = table.seat(sitter.seat);
            if (player == null || seat == null || sitter.bot) {
                continue;
            }
            String cards = seat.inHand() && !seat.folded() ? PokerTableShow.cardsText(seat.hole())
                    : label(player, "poker.bar-no-cards");
            String hand = "";
            if (seat.live() && table.board().size() >= 3) {
                hand = " &7(" + handName(player, PokerHand.best(seat.hole(), table.board()).category()) + "&7)";
            }
            String turn = table.toAct() == sitter.seat && step == Step.BET
                    ? label(player, "poker.bar-turn", "seconds", Math.max(0, actorTicks / 20)) : "";
            player.sendActionBar(Text.c(label(player, "poker.bar", "cards", cards, "hand", hand,
                    "stack", format(seat.stack()), "pot", format(table.pot()), "turn", turn)));
        }
    }

    private void showBet(int seat) {
        PokerTable.Seat at = table.seat(seat);
        if (show == null || at == null) {
            return;
        }
        show.bet(seat, at.bet(), bigBlindCents(), format(at.bet()));
    }

    private void refreshAllSeats() {
        for (int seat = 0; seat < table.size(); seat++) {
            refreshSeat(seat);
        }
    }

    /**
     * The plate over a chair (and its "sit" button when it is free).
     */
    private void refreshSeat(int seat) {
        if (show == null || seat < 0 || seat >= table.size()) {
            return;
        }
        PokerTable.Seat at = table.seat(seat);
        Sitter sitter = bySeat(seat);
        if (at == null || sitter == null) {
            show.plate(seat, Text.c(plugin.messages().get("poker.plate.free", "seat", seat + 1)), false, true);
            show.sitButton(seat, Text.c(plugin.messages().get("poker.plate.sit")),
                    player -> requestSeat(player, seat));
            return;
        }
        StringBuilder status = new StringBuilder();
        boolean active = table.toAct() == seat && step == Step.BET;
        if (active) {
            int seconds = sitter.bot ? 0 : Math.max(0, actorTicks / 20);
            status.append(plugin.messages().get(sitter.bot ? "poker.plate.thinking" : "poker.plate.turn",
                    "seconds", seconds));
        } else if (table.handRunning() && at.inHand() && at.folded()) {
            status.append(plugin.messages().get("poker.plate.folded"));
        } else if (table.handRunning() && at.allIn()) {
            status.append(plugin.messages().get("poker.plate.all-in"));
        } else if (sitter.leaving) {
            status.append(plugin.messages().get("poker.plate.leaving"));
        } else if (table.handRunning() && !at.inHand()) {
            status.append(plugin.messages().get("poker.plate.waiting"));
        }
        if (table.handRunning() && at.inHand()) {
            if (seat == table.smallBlindSeat() && table.street() == PokerTable.Street.PREFLOP) {
                status.append(status.length() > 0 ? " " : "").append(plugin.messages().get("poker.plate.small-blind"));
            } else if (seat == table.bigBlindSeat() && table.street() == PokerTable.Street.PREFLOP) {
                status.append(status.length() > 0 ? " " : "").append(plugin.messages().get("poker.plate.big-blind"));
            }
        }
        String text = plugin.messages().get(sitter.bot ? "poker.plate.house" : "poker.plate.player",
                "name", sitter.name, "stack", format(at.stack() + sitter.pendingChips),
                "status", status.length() == 0 ? "" : " &8· " + status);
        show.plate(seat, Text.c(text), active, false);
    }

    private String potText(long pot) {
        return pot <= 0 ? "" : plugin.messages().get("poker.plate.pot", "pot", format(pot));
    }

    // ============================================================= helpers

    private String handName(CommandSender viewer, PokerHand.Category category) {
        return viewer == null ? plugin.messages().get(category.key()) : label(viewer, category.key());
    }

    private Sitter bySeat(int seat) {
        for (Sitter sitter : sitters.values()) {
            if (sitter.seat == seat) {
                return sitter;
            }
        }
        return null;
    }

    private int humans() {
        int count = 0;
        for (Sitter sitter : sitters.values()) {
            if (!sitter.bot) {
                count++;
            }
        }
        return count;
    }

    private int bots() {
        return sitters.size() - humans();
    }

    private Player onlineHuman() {
        for (Sitter sitter : sitters.values()) {
            Player player = sitter.bot ? null : online(sitter.id);
            if (player != null) {
                return player;
            }
        }
        return null;
    }

    private Player online(UUID id) {
        return plugin.getServer().getPlayer(id);
    }

    private void tellKeyed(Player player, String key, Object... replacements) {
        player.sendMessage(plugin.messages().componentPlainFor(player, key, replacements));
    }

    private void tellTable(String key, Object... replacements) {
        tellTableExcept(null, key, replacements);
    }

    private void tellTableExcept(UUID except, String key, Object... replacements) {
        for (Sitter sitter : sitters.values()) {
            if (sitter.bot || sitter.id.equals(except)) {
                continue;
            }
            Player player = online(sitter.id);
            if (player != null) {
                tellKeyed(player, key, replacements);
            }
        }
    }

    private void soundTable(Sound sound) {
        for (Sitter sitter : sitters.values()) {
            Player player = sitter.bot ? null : online(sitter.id);
            if (player != null) {
                player.playSound(player.getLocation(), sound, 0.7f, 1.2f);
            }
        }
    }

    // ============================================================== menu

    @Override
    public List<String> statusLore(CommandSender viewer) {
        List<String> lore = new ArrayList<>();
        lore.add(label(viewer, "poker.status.seats", "seated", table.occupied(), "max", table.size()));
        lore.add(label(viewer, "poker.status.blinds", "small", format(smallBlindCents()),
                "big", format(bigBlindCents())));
        lore.add(label(viewer, "poker.status.buy-in", "min", format(minBuyIn()), "max", format(maxBuyIn())));
        if (table.handRunning()) {
            lore.add(label(viewer, "poker.status.hand", "hand", table.hands(), "pot", format(table.pot())));
        }
        return lore;
    }

    @Override
    public int activePlayers() {
        return humans();
    }

    @Override
    public boolean playableAgainstHouse() {
        return true;
    }

    // ========================================================== shutdown

    @Override
    public void shutdown() {
        // A hand cut short is void: everybody gets back what they put in it.
        table.abortHand();
        for (Sitter sitter : new ArrayList<>(sitters.values())) {
            sitter.leaving = true;
            leave(sitter);
        }
        if (show != null) {
            show.cancel();
            show = null;
        }
        step = Step.IDLE;
        saveState();
    }

    // ======================================================== persistence

    private File saveFile() {
        return new File(plugin.getDataFolder(), SAVE_FILE);
    }

    /**
     * Writes what every player would get back if the server stopped now: the stack they
     * had when the hand started (a hand cut short is void) plus the chips still waiting,
     * and the items they bought chips with.
     */
    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Sitter sitter : sitters.values()) {
            if (sitter.bot) {
                continue;
            }
            PokerTable.Seat seat = table.seat(sitter.seat);
            long stack = seat == null ? 0 : (table.handRunning() && seat.inHand() ? sitter.startStack : seat.stack());
            String path = "seats." + sitter.id;
            yaml.set(path + ".chips", stack + sitter.pendingChips);
            // The money part of those chips is what the bet journal owes if the server dies now.
            long owed = plan(sitter, stack + sitter.pendingChips).left();
            for (int i = 0; i < sitter.buyIns.size(); i++) {
                plugin.economy().ledger().setOwed(sitter.buyIns.get(i), i == 0 ? owed / 100.0 : 0);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (Escrow escrow : sitter.items) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("item", Base64.getEncoder().encodeToString(escrow.item().serializeAsBytes()));
                entry.put("count", escrow.count());
                entry.put("unit", escrow.unitCents());
                items.add(entry);
            }
            yaml.set(path + ".items", items);
        }
        try {
            if (yaml.getKeys(false).isEmpty()) {
                if (saveFile().exists() && !saveFile().delete()) {
                    plugin.getLogger().warning("Could not delete " + SAVE_FILE);
                }
                return;
            }
            yaml.save(saveFile());
        } catch (IOException error) {
            plugin.getLogger().severe("Could not save the poker table: " + error);
        }
    }

    /**
     * After a crash the table file still lists the players who were seated: they get
     * their chips back as money and items, exactly as if they had stood up.
     */
    private void refundCrashedTable() {
        File file = saveFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection seats = yaml.getConfigurationSection("seats");
        int refunded = 0;
        if (seats != null) {
            for (String key : seats.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    Sitter sitter = new Sitter(id, false, key, -1);
                    for (Map<?, ?> entry : seats.getMapList(key + ".items")) {
                        ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder()
                                .decode(String.valueOf(entry.get("item"))));
                        int count = ((Number) entry.get("count")).intValue();
                        long unit = ((Number) entry.get("unit")).longValue();
                        sitter.items.add(new Escrow(item, count, unit));
                    }
                    cashOut(sitter, seats.getLong(key + ".chips"), false);
                    refunded++;
                } catch (RuntimeException error) {
                    plugin.getLogger().severe("Could not refund the poker seat of " + key + ": " + error);
                }
            }
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete " + SAVE_FILE);
        }
        if (refunded > 0) {
            plugin.getLogger().warning("The poker table was not closed properly: " + refunded
                    + " players got their chips back.");
        }
    }
}
