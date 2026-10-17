package com.paulorchard.islandcraft.equipmentofarrakis.light;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The Personal Light: a toggle on the picker page (no item) that puts a torch-equivalent cool white light around the player, no
 * Mana, no drain. The light is the engine entity component {@code DynamicLight} (the same component the engine gives a dropped torch;
 * the client light of a held torch comes from the item and is not a server component), added when the toggle is ON and removed when it
 * is OFF. The state is the set of players who are ON; {@link Reconcile} makes the entities match it every tick, so a death, a
 * disconnect or a world change (the core clears the toggles) cannot leave a stuck light. {@code /arrakis light on|off|state}.
 */
public final class LightModule implements EquipmentModule {

    public static final String ID = "light";
    public static final String TOGGLE = "light";

    private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ADDED = ConcurrentHashMap.newKeySet();
    private static volatile ColorLight color = new ColorLight((byte) 0, (byte) 9, (byte) 10, (byte) 11);
    private static volatile String how = "defaults";
    private LightConfig config = new LightConfig();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        config = ctx.settings(ID, LightConfig.class);
        ToggleRegistry.get().register(new ToggleRegistry.Toggle(TOGGLE, 20, "server.customUI.arrakisPicker.light",
                "Furniture_Crude_Torch", (store, ref, player, on) -> set(player.getUuid(), on), ON::contains, ON::remove));
        ctx.system(new Reconcile());
        AbstractCommandCollection light = new AbstractCommandCollection("light", CommandRoot.LANG + "light.desc") {
        };
        light.addSubCommand(WorldPositional.build("on", CommandRoot.LANG + "light.on.desc", (c, w, s, a) -> player(c, s, true)));
        light.addSubCommand(WorldPositional.build("off", CommandRoot.LANG + "light.off.desc", (c, w, s, a) -> player(c, s, false)));
        light.addSubCommand(WorldPositional.build("state", CommandRoot.LANG + "light.state.desc", (c, w, s, a) -> state(c, s)));
        ctx.command(light);
    }

    @Override
    public void start(ModuleContext ctx) {
        resolveColor();
        ctx.logger().at(Level.INFO).log("Personal Light: %s", describe());
    }

    /** Reads the torch light (the Light of the torch block type) and applies the tint and the settings. */
    private void resolveColor() {
        int radius = 0;
        int r = 11;
        int g = 10;
        int b = 9;
        // The light of a torch is the Light of its block type (the item has none); the held torch is lit by the client from it.
        BlockType torch = BlockType.getAssetMap().getAsset(config.torchItemId());
        ColorLight torchLight = torch == null ? null : torch.getLight();
        if (torchLight != null) {
            radius = torchLight.radius;
            r = torchLight.red;
            g = torchLight.green;
            b = torchLight.blue;
            how = "matched to " + config.torchItemId() + " (radius " + radius + ", colour " + r + "/" + g + "/" + b + ")";
        } else {
            how = "the torch " + config.torchItemId() + " has no light: using the default torch numbers 0, 11/10/9";
        }
        LightColor.Rgb c = LightColor.resolve(radius, r, g, b, config.tintRed(), config.tintGreen(), config.tintBlue(),
                config.radius(), config.red(), config.green(), config.blue());
        color = new ColorLight((byte) c.radius(), (byte) c.red(), (byte) c.green(), (byte) c.blue());
    }

    private static String describe() {
        return "radius " + color.radius + ", colour " + color.red + "/" + color.green + "/" + color.blue + ", " + how;
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        boolean cool = LightColor.isCoolWhite(new LightColor.Rgb(color.radius, color.red, color.green, color.blue));
        lines.add((cool ? "" : "PROBLEM ") + "light " + describe() + (cool ? " (cool white)" : " (NOT cool white)"));
        lines.add(ToggleRegistry.get().find(TOGGLE) == null ? "PROBLEM the light toggle is not registered" : "toggle light registered");
        return lines;
    }

    static void set(UUID player, boolean on) {
        if (on) {
            ON.add(player);
        } else {
            ON.remove(player);
        }
    }

    // ---- commands

    private static void player(CommandContext c, Store<EntityStore> store, boolean on) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        PlayerRef player = store.getComponent(c.senderAsPlayerRef(), PlayerRef.getComponentType());
        set(player.getUuid(), on);
        CoreModule.say(c, "Personal Light: " + (on ? "ON" : "OFF"));
    }

    private static void state(CommandContext c, Store<EntityStore> store) {
        String mine = "";
        if (c.isPlayer() && c.senderAsPlayerRef() != null) {
            PlayerRef player = store.getComponent(c.senderAsPlayerRef(), PlayerRef.getComponentType());
            mine = (ON.contains(player.getUuid()) ? "ON" : "OFF") + " for you; ";
        }
        CoreModule.say(c, "Personal Light: " + mine + ON.size() + " player(s) on; " + describe());
    }

    // ---- the entities follow the state

    /** Every tick: a player who is ON (and alive) has the DynamicLight, any other player that this module lit does not. */
    private static final class Reconcile extends EntityTickingSystem<EntityStore> {

        private Query<EntityStore> query;

        @Override
        public Query<EntityStore> getQuery() {
            if (query == null) {
                query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType());
            }
            return query;
        }

        @Override
        public boolean isParallel(int archetypeChunkSize, int taskCount) {
            return false;
        }

        @Override
        public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                         CommandBuffer<EntityStore> commandBuffer) {
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null) {
                return;
            }
            UUID id = player.getUuid();
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            if (ON.contains(id) && chunk.getComponent(index, DeathComponent.getComponentType()) != null) {
                ON.remove(id);
            }
            boolean wanted = ON.contains(id);
            boolean has = chunk.getComponent(index, DynamicLight.getComponentType()) != null;
            if (wanted && !has) {
                commandBuffer.addComponent(ref, DynamicLight.getComponentType(), new DynamicLight(color));
                ADDED.add(id);
            } else if (wanted) {
                DynamicLight current = chunk.getComponent(index, DynamicLight.getComponentType());
                if (!color.equals(current.getColorLight())) {
                    current.setColorLight(color);
                }
            } else if (has && ADDED.remove(id)) {
                commandBuffer.removeComponent(ref, DynamicLight.getComponentType());
            }
        }
    }
}
