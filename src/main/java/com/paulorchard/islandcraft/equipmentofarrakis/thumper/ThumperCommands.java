package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.builtin.deployables.component.DeployableComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.paulorchard.islandcraft.equipmentofarrakis.core.worms.WormsLink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <pre>
 * /arrakis thumper give [n]     n Thumpers (default 4) into the hotbar, else the inventory
 * /arrakis thumper state        the standing Thumpers (owner, position, age, beat), the Worms link and the sand set
 * /arrakis thumper clear        removes every standing Thumper, for testing
 * </pre>
 */
final class ThumperCommands {

    private static final String LANG = CommandRoot.LANG + "thumper.";
    static final int DEFAULT_GIVE = 4;
    private static final int MAX_GIVE = 256;

    private ThumperCommands() {
    }

    static void register(com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection group) {
        group.addSubCommand(WorldPositional.build("give", LANG + "give.desc", (c, w, s, a) -> give(c, s, a), "count"));
        group.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> state(c)));
        group.addSubCommand(WorldPositional.build("clear", LANG + "clear.desc", (c, w, s, a) -> clear(c, s)));
    }

    /** The count typed after give: the default for none or nonsense, otherwise 1 to {@link #MAX_GIVE}. */
    static int parseCount(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            return DEFAULT_GIVE;
        }
        try {
            return Math.max(1, Math.min(MAX_GIVE, Integer.parseInt(args[0].trim())));
        } catch (NumberFormatException e) {
            return DEFAULT_GIVE;
        }
    }

    private static void give(CommandContext c, Store<EntityStore> store, String[] args) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        int wanted = parseCount(args);
        int left = wanted;
        while (left > 0) {
            ItemStack stack = new ItemStack(Thumpers.ID, left);
            ItemStack rest = hotbar.getInventory().addItemStack(stack).getRemainder();
            if (rest != null && !rest.isEmpty()) {
                rest = storage.getInventory().addItemStack(rest).getRemainder();
            }
            int remaining = rest == null || rest.isEmpty() ? 0 : rest.getQuantity();
            if (remaining >= left) {
                break; // nothing fitted
            }
            left = remaining;
        }
        CoreModule.say(c, left == 0 ? "gave " + wanted + " " + Thumpers.ID : "gave " + (wanted - left) + " of " + wanted + " " + Thumpers.ID + ", NO ROOM for " + left);
    }

    private static void state(CommandContext c) {
        long now = System.nanoTime();
        List<ThumperBook.Entry> entries = Thumpers.book().entries(now);
        ThumperConfig config = Thumpers.config();
        CoreModule.say(c, entries.size() + " standing Thumpers; beat every " + config.beatSeconds() + " s"
                + (config.doubleThumpGapSeconds() > 0 ? ", second hit " + config.doubleThumpGapSeconds() + " s later" : ", single hit")
                + "; at most " + config.maxLivePerPlayer() + " per player");
        for (ThumperBook.Entry e : entries) {
            CoreModule.say(c, String.format("  %s owner %s at %.1f %.1f %.1f, age %.0f s, beat timer %.2f s", e.id().toString().substring(0, 8),
                    e.owner(), e.x(), e.y(), e.z(), (now - e.firstSeenNanos()) / 1e9, e.phase()));
        }
        CoreModule.say(c, "Worms: " + WormsLink.status(Thumpers.ID));
        CoreModule.say(c, "sand blocks in use: " + new ArrayList<>(Thumpers.sand()));
    }

    private static void clear(CommandContext c, Store<EntityStore> store) {
        AtomicInteger removed = new AtomicInteger();
        store.forEachChunk(Archetype.of(DeployableComponent.getComponentType(), TransformComponent.getComponentType()), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                DeployableComponent deployable = chunk.getComponent(i, DeployableComponent.getComponentType());
                if (deployable != null && deployable.getConfig() != null && Thumpers.ID.equals(deployable.getConfig().getId())) {
                    buffer.removeEntity(chunk.getReferenceTo(i), RemoveReason.REMOVE);
                    removed.incrementAndGet();
                }
            }
        });
        Thumpers.book().clear();
        CoreModule.say(c, "removed " + removed.get() + " Thumpers");
    }
}
