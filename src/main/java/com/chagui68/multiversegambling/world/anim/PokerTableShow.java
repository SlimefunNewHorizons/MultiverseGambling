package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Card;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The poker room: a big oval table lying flat in the middle of the pavilion, eight
 * chairs round it, and the dealer standing on the far side with the shoe.
 *
 * <p>Everything the game knows is drawn here, but the show never decides anything: the
 * game tells it which cards were dealt, who bet what and who won, and the show makes it
 * visible. What each player may see is enforced with per player visibility: the hole
 * cards lie face down for the whole room, while their owner alone sees them face up
 * (and a bigger copy floating in front of them), so nobody can read somebody else's hand
 * just by walking round the table. The buttons of a player exist for that player only,
 * so eight people can have their controls in the same space without getting in each
 * other's way.</p>
 *
 * <p>The table is laid out in layers so nothing floats in front of anything else: the
 * cards, chips and their small labels lie on the felt, the board and the pot share one
 * low sign in the middle, the buttons of a player sit in a single spot just over the rail
 * in front of their chair (the ones to act on the turn, the seat ones otherwise) and the
 * name plates hang high over the chairs, above the name tags of the players.</p>
 *
 * <p>Local frame: the audience stands on {@code +z} (the main gate), the dealer on
 * {@code -z}; seats go round the table clockwise as seen from above, starting on the
 * dealer's left, the way the action goes at a real table.</p>
 */
public final class PokerTableShow extends ArenaShow {

    /** Seats round the table. */
    public static final int SEATS = 8;

    /** Half length and half width of the felt. */
    private static final double A = 4.2;
    private static final double B = 2.3;
    /** Half axes of the ring the chairs stand on. */
    private static final double SEAT_A = A + 1.5;
    private static final double SEAT_B = B + 1.5;
    /** Height of the felt. */
    private static final float TOP = 1.0f;
    /** Where a seated player's vehicle goes. */
    private static final double VEHICLE_Y = 0.55;
    private static final double DEALER_Z = -(B + 0.95);
    /** Height of the name plates: over the name tag of a seated player. */
    private static final double PLATE_Y = 3.05;
    /** Distance from a chair to its buttons: over the rail, within easy reach. */
    private static final double BUTTON_REACH = 1.05;
    /** Heights of the two rows of buttons, below the eyes of a seated player. */
    private static final double LOWER_ROW_Y = 1.15;
    private static final double UPPER_ROW_Y = 1.5;
    /** Middle of the sign with the board and the pot. */
    private static final double SIGN_Z = 0.1;

    private static final Color FACE = Color.fromARGB(255, 250, 250, 242);
    private static final Color BACK = Color.fromARGB(255, 150, 22, 28);
    private static final Color PLATE = Color.fromARGB(150, 10, 10, 18);
    private static final Color PLATE_ACTIVE = Color.fromARGB(200, 150, 110, 10);
    private static final Color PLATE_EMPTY = Color.fromARGB(90, 10, 10, 18);

    /** Everything that belongs to one seat. */
    private static final class SeatProps {
        private TextDisplay plate;
        private HoloButton sit;
        private ItemDisplay vehicle;
        private final List<Entity> hole = new ArrayList<>();
        private final List<Entity> chips = new ArrayList<>();
        private TextDisplay betLabel;
        private TextDisplay handLabel;
        private final List<HoloButton> controls = new ArrayList<>();
        private final List<HoloButton> utility = new ArrayList<>();
        /** The seat buttons, kept so they come back once the turn is over. */
        private List<HoloButton.Spec> utilitySpecs = List.of();
        private UUID utilityOwner;
    }

    private final Map<Integer, SeatProps> seats = new HashMap<>();
    private final List<Entity> board = new ArrayList<>();
    private final List<Entity> potChips = new ArrayList<>();
    private Location anchor;
    private LivingEntity dealer;
    private TextDisplay boardLabel;
    private TextDisplay potLabel;
    private TextDisplay speech;
    private int speechUntil;
    private Entity dealerButton;
    private int age;

    public PokerTableShow(MultiverseGamblingPlugin plugin, ArenaStage stage) {
        super(plugin, stage, 1);
    }

    // ------------------------------------------------------------- geometry

    private static double angle(int seat) {
        return Math.toRadians(140.0 - 40.0 * seat);
    }

    /** Local x of a chair. */
    public static double seatX(int seat) {
        return SEAT_A * Math.sin(angle(seat));
    }

    /** Local z of a chair. */
    public static double seatZ(int seat) {
        return SEAT_B * Math.cos(angle(seat));
    }

    /** A point on the felt in the direction of a seat, {@code fraction} of the way to the rail. */
    private static double[] edge(int seat, double fraction) {
        return new double[]{A * fraction * Math.sin(angle(seat)), B * fraction * Math.cos(angle(seat))};
    }

    /** Unit vector from a seat towards the middle of the table. */
    private static double[] inward(int seat) {
        double x = -seatX(seat);
        double z = -seatZ(seat);
        double n = Math.hypot(x, z);
        return new double[]{x / n, z / n};
    }

    /** Rotation laying a card flat, readable from the seat. */
    private static Quaternionf flatTowards(int seat) {
        double[] in = inward(seat);
        // The reader sits opposite the inward direction.
        double yaw = Math.atan2(-in[0], -in[1]);
        return new Quaternionf().rotateY((float) yaw).rotateX((float) (-Math.PI / 2));
    }

    /** Rotation laying a card flat, readable from the audience side. */
    private static Quaternionf flatFront() {
        return new Quaternionf().rotateX((float) (-Math.PI / 2));
    }

    /**
     * Where a player sits, looking at the middle of the table.
     */
    public Location seatSpot(int seat) {
        Location spot = local(seatX(seat), VEHICLE_Y, seatZ(seat));
        spot.setYaw(stage().frame().lookYaw(seatX(seat), seatZ(seat), 0, 0));
        spot.setPitch(15.0f);
        return spot;
    }

    // ---------------------------------------------------------------- build

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        buildTable();
        for (int seat = 0; seat < SEATS; seat++) {
            buildChair(seat);
            SeatProps props = new SeatProps();
            props.plate = text(local(seatX(seat), PLATE_Y, seatZ(seat)), Text.c("&7—"), Props.scaled(0.65f),
                    Display.Billboard.CENTER);
            props.plate.setBackgroundColor(PLATE_EMPTY);
            seats.put(seat, props);
        }
        dealer = spawnDealer();
        // One low sign in the middle: the board on top, the pot right under it.
        boardLabel = text(local(0, TOP + 0.8, SIGN_Z), Text.c(""), Props.scaled(0.75f), Display.Billboard.VERTICAL);
        boardLabel.setBackgroundColor(Props.argb(0, 0, 0, 0));
        potLabel = text(local(0, TOP + 0.5, SIGN_Z), Text.c(""), Props.scaled(0.6f), Display.Billboard.VERTICAL);
        potLabel.setBackgroundColor(Props.argb(0, 0, 0, 0));
        speech = text(local(0, 3.2, DEALER_Z), Text.c(""), Props.scaled(0.7f), Display.Billboard.CENTER);
        speech.setBackgroundColor(Props.argb(0, 0, 0, 0));
        playSound(Sound.BLOCK_WOOD_PLACE, 0.8f, 1.2f);
    }

    private void buildTable() {
        // Pedestal and the dark wooden body.
        block(anchor, Material.BLACK_CONCRETE, Props.box(new Vector3f(0, 0.15f, 0), new Vector3f(2.4f, 0.3f, 1.2f)));
        block(anchor, Material.POLISHED_BLACKSTONE, Props.box(new Vector3f(0, 0.45f, 0), new Vector3f(1.2f, 0.6f, 0.6f)));
        slices(A - 0.35, B - 0.3, 0.62f, 0.5f, Material.DARK_OAK_PLANKS);
        // The felt.
        slices(A, B, TOP - 0.05f, 0.1f, Material.GREEN_WOOL);
        // A gold line round the betting area and the padded rail.
        ring(A * 0.74, B * 0.74, TOP + 0.003f, 0.012f, 0.05f, Material.GOLD_BLOCK, 36);
        ring(A + 0.18, B + 0.18, TOP + 0.02f, 0.2f, 0.42f, Material.BLACK_WOOL, 44);
        ring(A + 0.42, B + 0.42, TOP - 0.02f, 0.14f, 0.12f, Material.DARK_OAK_PLANKS, 48);
        // The shoe and the chip tray in front of the dealer.
        block(anchor, Material.BLACK_CONCRETE, Props.box(new Vector3f(0.75f, TOP + 0.08f, (float) (-B + 0.45)),
                new Vector3f(0.32f, 0.16f, 0.42f)));
        block(anchor, Material.RED_CONCRETE, Props.box(new Vector3f(0.75f, TOP + 0.165f, (float) (-B + 0.45)),
                new Vector3f(0.24f, 0.01f, 0.34f)));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(-0.4f, TOP + 0.03f, (float) (-B + 0.35)),
                new Vector3f(1.1f, 0.06f, 0.3f)));
        Material[] tray = {Material.WHITE_CONCRETE, Material.RED_CONCRETE, Material.LIME_CONCRETE,
                Material.BLACK_CONCRETE, Material.PURPLE_CONCRETE};
        for (int i = 0; i < tray.length; i++) {
            block(anchor, tray[i], Props.box(new Vector3f(-0.82f + i * 0.21f, TOP + 0.09f, (float) (-B + 0.35)),
                    new Vector3f(0.17f, 0.08f, 0.22f)));
        }
        // The name of the game printed on the felt.
        text(local(0, TOP + 0.012, 1.15), Text.c("&2&l♠ &4&l♥  &a&lMULTIVERSE POKER  &4&l♦ &2&l♣"),
                Props.centred(new Vector3f(), flatFront(), new Vector3f(1.3f, 1.3f, 1.3f)), Display.Billboard.FIXED)
                .setShadowed(false);
    }

    /** An oval made of thin strips laid side by side. */
    private void slices(double a, double b, float y, float height, Material material) {
        double step = 0.22;
        for (double z = -b + step / 2; z < b; z += step) {
            double half = a * Math.sqrt(Math.max(0, 1 - (z * z) / (b * b)));
            if (half < 0.05) {
                continue;
            }
            block(anchor, material, Props.box(new Vector3f(0, y, (float) z),
                    new Vector3f((float) (2 * half), height, (float) (step + 0.02))));
        }
    }

    /** A band round an oval, made of short straight pieces. */
    private void ring(double a, double b, float y, float height, float width, Material material, int pieces) {
        for (int i = 0; i < pieces; i++) {
            double p0 = 2 * Math.PI * i / pieces;
            double p1 = 2 * Math.PI * (i + 1) / pieces;
            double x0 = a * Math.cos(p0);
            double z0 = b * Math.sin(p0);
            double x1 = a * Math.cos(p1);
            double z1 = b * Math.sin(p1);
            double dx = x1 - x0;
            double dz = z1 - z0;
            float length = (float) Math.hypot(dx, dz) + 0.03f;
            float yaw = (float) Math.atan2(-dz, dx);
            block(anchor, material, Props.box(new Vector3f((float) ((x0 + x1) / 2), y, (float) ((z0 + z1) / 2)),
                    new Quaternionf().rotateY(yaw), new Vector3f(length, height, width)));
        }
    }

    private void buildChair(int seat) {
        double[] in = inward(seat);
        float yaw = (float) Math.atan2(-in[0], -in[1]);
        Quaternionf turn = new Quaternionf().rotateY(yaw);
        Vector3f middle = new Vector3f((float) seatX(seat), 0, (float) seatZ(seat));
        // Seat, legs and the backrest on the far side from the table.
        block(anchor, Material.RED_WOOL, Props.box(new Vector3f(middle).add(0, 0.46f, 0), turn,
                new Vector3f(0.7f, 0.12f, 0.7f)));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(middle).add(0, 0.2f, 0), turn,
                new Vector3f(0.18f, 0.4f, 0.18f)));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(middle).add(0, 0.03f, 0), turn,
                new Vector3f(0.6f, 0.06f, 0.6f)));
        Vector3f back = new Vector3f(middle).add((float) (-in[0] * 0.36), 0.9f, (float) (-in[1] * 0.36));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(back, turn, new Vector3f(0.74f, 0.82f, 0.1f)));
        block(anchor, Material.RED_WOOL, Props.box(new Vector3f(back).add((float) (in[0] * 0.06), 0.02f,
                (float) (in[1] * 0.06)), turn, new Vector3f(0.6f, 0.62f, 0.04f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(back).add(0, 0.43f, 0), turn,
                new Vector3f(0.78f, 0.05f, 0.12f)));
    }

    /**
     * The dealer: a mannequin in a black suit where the server has them, a villager in an
     * apron on older servers.
     */
    private LivingEntity spawnDealer() {
        Location spot = local(0, 0, DEALER_Z);
        try {
            return DealerFactory.mannequin(this, spot);
        } catch (Throwable missing) {
            return spawn(spot, org.bukkit.entity.Villager.class, villager -> {
                villager.setAI(false);
                villager.setSilent(true);
                villager.setInvulnerable(true);
                villager.setCollidable(false);
                villager.setProfession(org.bukkit.entity.Villager.Profession.LIBRARIAN);
                villager.customName(Text.c("&6&lDealer"));
                villager.setCustomNameVisible(true);
            });
        }
    }

    /**
     * Kept apart so a server without mannequins never loads the class that names them.
     */
    private static final class DealerFactory {
        static LivingEntity mannequin(PokerTableShow show, Location spot) {
            return show.spawn(spot, org.bukkit.entity.Mannequin.class, dealer -> {
                dealer.setImmovable(true);
                dealer.setInvulnerable(true);
                dealer.setGravity(false);
                dealer.setSilent(true);
                dealer.setCollidable(false);
                dealer.customName(Text.c("&6&lDealer"));
                dealer.setCustomNameVisible(true);
                dealer.setDescription(Text.c("&7♠ ♥ ♦ ♣"));
                org.bukkit.inventory.EntityEquipment gear = dealer.getEquipment();
                gear.setChestplate(dyed(Material.LEATHER_CHESTPLATE, Color.fromRGB(0x151515)));
                gear.setLeggings(dyed(Material.LEATHER_LEGGINGS, Color.fromRGB(0x151515)));
                gear.setBoots(dyed(Material.LEATHER_BOOTS, Color.fromRGB(0x0A0A0A)));
                gear.setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.PAPER));
            });
        }

        private static org.bukkit.inventory.ItemStack dyed(Material material, Color colour) {
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
                meta.setColor(colour);
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    // -------------------------------------------------------------- seating

    /**
     * Puts the "sit here" button on a free chair, or takes it away.
     */
    public void sitButton(int seat, Component label, Consumer<Player> action) {
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        if (label != null && props.sit != null && props.sit.alive()) {
            props.sit.text(label);
            return;
        }
        if (props.sit != null) {
            props.sit.remove();
            props.sit = null;
        }
        if (label != null) {
            props.sit = addButton(seatX(seat), 1.35, seatZ(seat), label, HoloButton.GREEN, 1.0f, null, action);
        }
    }

    /**
     * Seats a player on a chair.
     */
    public boolean mount(int seat, Player player) {
        SeatProps props = seats.get(seat);
        if (props == null || !player.isOnline()) {
            return false;
        }
        if (props.vehicle == null || !props.vehicle.isValid()) {
            props.vehicle = spawn(local(seatX(seat), VEHICLE_Y, seatZ(seat)), ItemDisplay.class, seatEntity -> {
                seatEntity.setItemStack(null);
            });
        }
        if (props.vehicle.getPassengers().contains(player)) {
            return true;
        }
        player.leaveVehicle();
        player.teleport(seatSpot(seat));
        return props.vehicle.addPassenger(player);
    }

    /**
     * Stands a player up from a chair.
     */
    public void unmount(int seat) {
        SeatProps props = seats.get(seat);
        if (props == null || props.vehicle == null) {
            return;
        }
        for (Entity passenger : new ArrayList<>(props.vehicle.getPassengers())) {
            props.vehicle.removePassenger(passenger);
            if (passenger instanceof Player player) {
                Location out = local(seatX(seat) * 1.18, 0, seatZ(seat) * 1.18);
                out.setYaw(seatSpot(seat).getYaw());
                player.teleport(out);
            }
        }
    }

    /** True when that entity is the chair of a seat. */
    public int seatOfVehicle(Entity vehicle) {
        for (Map.Entry<Integer, SeatProps> entry : seats.entrySet()) {
            if (entry.getValue().vehicle != null && entry.getValue().vehicle.equals(vehicle)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Rewrites the name plate over a chair.
     */
    public void plate(int seat, Component text, boolean active, boolean empty) {
        SeatProps props = seats.get(seat);
        if (props == null || props.plate == null || !props.plate.isValid()) {
            return;
        }
        props.plate.text(text);
        props.plate.setBackgroundColor(active ? PLATE_ACTIVE : empty ? PLATE_EMPTY : PLATE);
    }

    // ---------------------------------------------------------------- cards

    private Location deck() {
        return local(0.75, TOP + 0.2, -B + 0.45);
    }

    private static Component face(Card card) {
        boolean red = card.suit() == Card.Suit.HEARTS || card.suit() == Card.Suit.DIAMONDS;
        String colour = red ? "&4&l" : "&0&l";
        return Text.c(colour + card.rankLabel() + "\n" + colour + card.suit().glyph());
    }

    /** Short text of a few cards, coloured for a dark background. */
    public static String cardsText(List<Card> cards) {
        StringBuilder out = new StringBuilder();
        for (Card card : cards) {
            boolean red = card.suit() == Card.Suit.HEARTS || card.suit() == Card.Suit.DIAMONDS;
            out.append(red ? "&c&l" : "&f&l").append(card.rankLabel()).append(card.suit().glyph()).append(' ');
        }
        return out.toString().trim();
    }

    /**
     * A card that flies from the shoe to its spot on the felt and lands there flat.
     *
     * @param onlyFor  the only player who sees it, or {@code null}
     * @param hideFrom a player who must not see it, or {@code null}
     */
    private TextDisplay flyCard(Card card, boolean faceDown, double x, double z, Quaternionf rotation,
                                float scale, int delay, UUID onlyFor, UUID hideFrom, List<Entity> into) {
        Player viewer = onlyFor == null ? null : plugin().getServer().getPlayer(onlyFor);
        Player hidden = hideFrom == null ? null : plugin().getServer().getPlayer(hideFrom);
        TextDisplay display = spawn(deck(), TextDisplay.class, entity -> {
            if (onlyFor != null) {
                HoloButton.privateTo(this, entity, viewer);
            }
            if (hidden != null) {
                hidden.hideEntity(plugin(), entity);
            }
            entity.text(faceDown || card == null ? Text.c("&f&l✦\n&f&l✦") : face(card));
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setBackgroundColor(faceDown || card == null ? BACK : FACE);
            entity.setShadowed(false);
            entity.setLineWidth(60);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setTeleportDuration(7);
            entity.setTransformation(Props.centred(new Vector3f(), rotation, new Vector3f(0.15f, 0.15f, 0.15f)));
        });
        into.add(display);
        later(1 + delay, () -> {
            if (!display.isValid()) {
                return;
            }
            display.teleport(local(x, TOP + 0.015, z));
            Props.animate(display, Props.centred(new Vector3f(), rotation, new Vector3f(scale, scale, scale)), 7);
        });
        return display;
    }

    /**
     * Deals the two hole cards of a seat: face down for the room, face up for their owner
     * alone (who also reads them in the action bar).
     *
     * @param owner the player of the seat, or {@code null} for the house
     */
    public void dealHole(int seat, UUID owner, List<Card> cards, int delay) {
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        double[] spot = edge(seat, 0.8);
        double[] in = inward(seat);
        Quaternionf rotation = flatTowards(seat);
        for (int i = 0; i < cards.size(); i++) {
            double side = (i - 0.5) * 0.36;
            double x = spot[0] - in[1] * side;
            double z = spot[1] + in[0] * side;
            flyCard(null, true, x, z, rotation, 0.55f, delay + i * 6, null, owner, props.hole);
            if (owner != null) {
                flyCard(cards.get(i), false, x, z, rotation, 0.55f, delay + i * 6, owner, null, props.hole);
            }
        }
        later(delay, () -> {
            swing();
            playSound(Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.4f);
        });
    }

    /**
     * Throws the cards of a seat into the muck.
     */
    public void fold(int seat) {
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        for (Entity card : new ArrayList<>(props.hole)) {
            if (card instanceof Display display && card.isValid()) {
                display.setTeleportDuration(6);
                display.teleport(local(0, TOP + 0.02, -B + 0.7));
                Props.animate(display, Props.scaled(0.05f), 6);
            }
            later(7, () -> discard(card));
        }
        props.hole.clear();
        if (props.handLabel != null) {
            discard(props.handLabel);
            props.handLabel = null;
        }
        playSound(Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 0.7f);
    }

    /**
     * Shows the hole cards of a seat to everybody, with the name of the hand.
     */
    public void reveal(int seat, List<Card> cards, String handName, boolean winner) {
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        for (Entity card : new ArrayList<>(props.hole)) {
            discard(card);
        }
        props.hole.clear();
        double[] spot = edge(seat, 0.8);
        double[] in = inward(seat);
        Quaternionf rotation = flatTowards(seat);
        for (int i = 0; i < cards.size(); i++) {
            double side = (i - 0.5) * 0.36;
            double x = spot[0] - in[1] * side;
            double z = spot[1] + in[0] * side;
            TextDisplay card = spawn(local(x, TOP + 0.015, z), TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setBackgroundColor(FACE);
                entity.setShadowed(false);
                entity.setLineWidth(60);
                entity.setTransformation(Props.centred(new Vector3f(), rotation, new Vector3f(0.02f, 0.55f, 0.55f)));
            });
            card.text(face(cards.get(i)));
            Props.animate(card, Props.centred(new Vector3f(), rotation, new Vector3f(0.55f, 0.55f, 0.55f)), 4);
            if (winner) {
                card.setGlowColorOverride(Color.fromRGB(0xFFD700));
                card.setGlowing(true);
            }
            props.hole.add(card);
        }
        if (props.handLabel != null) {
            discard(props.handLabel);
        }
        double[] near = edge(seat, 0.8);
        props.handLabel = text(local(near[0], TOP + 0.4, near[1]),
                Text.c((winner ? "&6&l" : "&f") + cardsText(cards) + "\n" + (winner ? "&e&l" : "&7") + handName),
                Props.scaled(0.5f), Display.Billboard.VERTICAL);
        props.handLabel.setBackgroundColor(Props.argb(170, 0, 0, 0));
        playSound(Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
    }

    /**
     * Brings the community cards up to date, dealing the new ones from the shoe.
     */
    public void board(List<Card> cards, int delay) {
        int start = board.size();
        for (int i = start; i < cards.size(); i++) {
            double x = -1.44 + i * 0.72;
            flyCard(cards.get(i), false, x, -0.3, flatFront(), 0.75f, delay + (i - start) * 5, null, null, board);
        }
        if (cards.size() > start) {
            later(delay, this::swing);
        }
        later(delay + 6 + (cards.size() - start) * 5, () -> {
            if (boardLabel != null && boardLabel.isValid()) {
                boardLabel.text(Text.c(cardsText(cards)));
                boardLabel.setBackgroundColor(cards.isEmpty() ? Props.argb(0, 0, 0, 0) : Props.argb(170, 10, 10, 14));
            }
        });
    }

    /**
     * Highlights the cards of the board that make the winning hand.
     */
    public void glowBoard(List<Card> winning, List<Card> boardCards) {
        for (int i = 0; i < board.size() && i < boardCards.size(); i++) {
            if (board.get(i) instanceof Display display && winning.contains(boardCards.get(i))) {
                display.setGlowColorOverride(Color.fromRGB(0xFFD700));
                display.setGlowing(true);
            }
        }
    }

    // ---------------------------------------------------------------- chips

    private static Material chipColour(long amount, long bigBlind) {
        double blinds = amount / (double) Math.max(1, bigBlind);
        if (blinds <= 1) {
            return Material.WHITE_CONCRETE;
        }
        if (blinds <= 5) {
            return Material.RED_CONCRETE;
        }
        if (blinds <= 25) {
            return Material.LIME_CONCRETE;
        }
        if (blinds <= 100) {
            return Material.BLACK_CONCRETE;
        }
        if (blinds <= 500) {
            return Material.PURPLE_CONCRETE;
        }
        return Material.GOLD_BLOCK;
    }

    private static int chipCount(long amount, long bigBlind) {
        double blinds = amount / (double) Math.max(1, bigBlind);
        return (int) Math.max(1, Math.min(8, 1 + Math.floor(Math.log(Math.max(1, blinds)) / Math.log(2))));
    }

    private void stack(List<Entity> into, double x, double z, long amount, long bigBlind) {
        int count = chipCount(amount, bigBlind);
        Material colour = chipColour(amount, bigBlind);
        Location base = local(x, 0, z);
        for (int i = 0; i < count; i++) {
            into.add(block(base, i % 3 == 2 ? Material.WHITE_CONCRETE : colour,
                    Props.box(new Vector3f(0, TOP + 0.025f + i * 0.045f, 0), new Vector3f(0.22f, 0.04f, 0.22f))));
        }
    }

    /**
     * Shows what a seat has in front of it this betting round.
     */
    public void bet(int seat, long amount, long bigBlind, String label) {
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        for (Entity chip : props.chips) {
            discard(chip);
        }
        props.chips.clear();
        if (props.betLabel != null) {
            discard(props.betLabel);
            props.betLabel = null;
        }
        if (amount <= 0) {
            return;
        }
        double[] spot = edge(seat, 0.56);
        stack(props.chips, spot[0], spot[1], amount, bigBlind);
        props.betLabel = text(local(spot[0], TOP + 0.45, spot[1]), Text.c("&e" + label), Props.scaled(0.42f),
                Display.Billboard.CENTER);
        props.betLabel.setBackgroundColor(Props.argb(140, 0, 0, 0));
        playSound(Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.6f);
    }

    /**
     * Slides every bet into the pot.
     */
    public void collect(long pot, long bigBlind, String potText) {
        Location middle = local(0, TOP + 0.03, 0.55);
        for (SeatProps props : seats.values()) {
            for (Entity chip : props.chips) {
                if (chip instanceof Display display && chip.isValid()) {
                    display.setTeleportDuration(8);
                    display.teleport(middle);
                }
                later(9, () -> discard(chip));
            }
            props.chips.clear();
            if (props.betLabel != null) {
                discard(props.betLabel);
                props.betLabel = null;
            }
        }
        later(9, () -> pot(pot, bigBlind, potText));
        playSound(Sound.BLOCK_CHAIN_STEP, 0.8f, 1.2f);
    }

    /**
     * Redraws the pile of the pot and its label.
     */
    public void pot(long pot, long bigBlind, String potText) {
        for (Entity chip : potChips) {
            discard(chip);
        }
        potChips.clear();
        if (pot > 0) {
            stack(potChips, -0.25, 0.55, pot, bigBlind);
            stack(potChips, 0.05, 0.62, pot / 2, bigBlind);
            stack(potChips, 0.3, 0.5, pot / 3, bigBlind);
        }
        if (potLabel != null && potLabel.isValid()) {
            potLabel.text(Text.c(potText == null ? "" : potText));
            potLabel.setBackgroundColor(pot > 0 ? Props.argb(170, 60, 40, 0) : Props.argb(0, 0, 0, 0));
        }
    }

    /**
     * Pushes the pot over to the seats that won it.
     */
    public void push(List<Integer> winners) {
        if (winners.isEmpty()) {
            return;
        }
        int i = 0;
        for (Entity chip : potChips) {
            int seat = winners.get(i++ % winners.size());
            double[] spot = edge(seat, 0.7);
            if (chip instanceof Display display && chip.isValid()) {
                display.setTeleportDuration(12);
                display.teleport(local(spot[0], 0, spot[1]));
            }
            later(16, () -> discard(chip));
        }
        potChips.clear();
        if (potLabel != null && potLabel.isValid()) {
            later(16, () -> potLabel.text(Text.c("")));
        }
        playSound(Sound.BLOCK_CHAIN_FALL, 1.0f, 1.0f);
    }

    /**
     * Moves the dealer button in front of a seat.
     */
    public void dealerButton(int seat) {
        double[] spot = edge(seat, 0.7);
        double[] in = inward(seat);
        Location at = local(spot[0] + in[1] * 0.45, 0, spot[1] - in[0] * 0.45);
        if (dealerButton == null || !dealerButton.isValid()) {
            dealerButton = spawn(at, TextDisplay.class, entity -> {
                entity.text(Text.c("&0&lD"));
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setBackgroundColor(Props.argb(255, 245, 245, 245));
                entity.setShadowed(false);
                entity.setTeleportDuration(10);
                entity.setTransformation(Props.centred(new Vector3f(0, TOP + 0.02f, 0), flatFront(),
                        new Vector3f(0.7f, 0.7f, 0.7f)));
            });
        } else {
            dealerButton.teleport(at);
        }
    }

    // ------------------------------------------------------------- controls

    /**
     * The buttons of the player to act, for them alone: a lower row (fold, check or
     * call, all in) and an upper row to size a raise. They take the place of the seat
     * buttons until the turn is over.
     */
    public void controls(int seat, UUID owner, List<HoloButton.Spec> lower, List<HoloButton.Spec> upper) {
        clearControls(seat);
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        removeUtility(props);
        if (!lower.isEmpty()) {
            props.controls.addAll(row(seat, LOWER_ROW_Y, owner, lower));
        }
        if (!upper.isEmpty()) {
            props.controls.addAll(row(seat, UPPER_ROW_Y, owner, upper));
        }
    }

    /** A private row of buttons on the rail in front of a chair. */
    private List<HoloButton> row(int seat, double y, UUID owner, List<HoloButton.Spec> specs) {
        double[] in = inward(seat);
        return addControlRowAt(seatX(seat), seatZ(seat), in[0], in[1], BUTTON_REACH, y, owner, true, specs);
    }

    /**
     * Changes the label of one of the buttons of a seat, keeping its hitbox.
     */
    public void relabel(int seat, int index, Component text) {
        SeatProps props = seats.get(seat);
        if (props != null && index >= 0 && index < props.controls.size()) {
            props.controls.get(index).text(text);
        }
    }

    public void clearControls(int seat) {
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        boolean had = !props.controls.isEmpty();
        for (HoloButton button : props.controls) {
            button.remove();
        }
        props.controls.clear();
        if (had) {
            // The turn is over: the seat buttons come back in the same spot.
            buildUtility(seat, props);
        }
    }

    /**
     * Small private buttons that stay while the player is seated (leave, house...).
     */
    public void utility(int seat, UUID owner, List<HoloButton.Spec> specs) {
        SeatProps props = seats.get(seat);
        if (props == null) {
            return;
        }
        props.utilityOwner = owner;
        props.utilitySpecs = owner == null ? List.of() : List.copyOf(specs);
        removeUtility(props);
        if (props.controls.isEmpty()) {
            buildUtility(seat, props);
        }
    }

    private void buildUtility(int seat, SeatProps props) {
        removeUtility(props);
        if (props.utilityOwner != null && !props.utilitySpecs.isEmpty()) {
            props.utility.addAll(row(seat, LOWER_ROW_Y, props.utilityOwner, props.utilitySpecs));
        }
    }

    private static void removeUtility(SeatProps props) {
        for (HoloButton button : props.utility) {
            button.remove();
        }
        props.utility.clear();
    }

    // ------------------------------------------------------------ the dealer

    /**
     * Something the dealer says, over their head for a few seconds.
     */
    public void say(String legacy) {
        if (speech == null || !speech.isValid()) {
            return;
        }
        speech.text(Text.c(legacy));
        speech.setBackgroundColor(Props.argb(170, 0, 0, 0));
        speechUntil = age + 70;
    }

    private void swing() {
        if (dealer != null && dealer.isValid()) {
            dealer.swingMainHand();
        }
    }

    /**
     * Fireworks over the seats that won.
     */
    public void celebrate(List<Integer> winners) {
        for (int seat : winners) {
            double[] spot = edge(seat, 0.9);
            particles(Particle.TOTEM_OF_UNDYING, spot[0], TOP + 0.8, spot[1], 30, 0.5, 0.35);
            particles(Particle.FIREWORK, spot[0], TOP + 1.0, spot[1], 20, 0.4, 0.1);
        }
        playSound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.9f, 1.1f);
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }

    /**
     * Clears the cards and chips of the last hand.
     */
    public void clearHand() {
        for (SeatProps props : seats.values()) {
            for (Entity card : props.hole) {
                discard(card);
            }
            props.hole.clear();
            for (Entity chip : props.chips) {
                discard(chip);
            }
            props.chips.clear();
            if (props.betLabel != null) {
                discard(props.betLabel);
                props.betLabel = null;
            }
            if (props.handLabel != null) {
                discard(props.handLabel);
                props.handLabel = null;
            }
        }
        for (Entity card : board) {
            discard(card);
        }
        board.clear();
        for (Entity chip : potChips) {
            discard(chip);
        }
        potChips.clear();
        if (boardLabel != null && boardLabel.isValid()) {
            boardLabel.text(Text.c(""));
            boardLabel.setBackgroundColor(Props.argb(0, 0, 0, 0));
        }
        if (potLabel != null && potLabel.isValid()) {
            potLabel.text(Text.c(""));
            potLabel.setBackgroundColor(Props.argb(0, 0, 0, 0));
        }
    }

    /**
     * True while the scenery is standing: the chunks it lives in may have unloaded it.
     */
    public boolean intact() {
        return active() && dealer != null && dealer.isValid() && boardLabel != null && boardLabel.isValid();
    }

    @Override
    protected void onAnimate(int tick) {
        age = tick;
        if (speech != null && speech.isValid() && speechUntil > 0 && age >= speechUntil) {
            speech.text(Text.c(""));
            speech.setBackgroundColor(Props.argb(0, 0, 0, 0));
            speechUntil = 0;
        }
        // The dealer keeps an eye on the whole table.
        if (dealer != null && dealer.isValid() && age % 40 == 0) {
            Location look = dealer.getLocation();
            float base = stage().yaw();
            look.setYaw(base + (float) (Math.sin(age / 90.0) * 35.0));
            dealer.setRotation(look.getYaw(), 8.0f);
        }
    }

    @Override
    protected void onCancel() {
        for (int seat = 0; seat < SEATS; seat++) {
            unmount(seat);
        }
    }

    @Override
    protected void onSettle() {
        for (int seat = 0; seat < SEATS; seat++) {
            unmount(seat);
        }
    }
}
