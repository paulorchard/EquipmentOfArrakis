package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Suspensor Belt: one toggle on the picker page. While it is on, the movement setting {@code fly} is Allowed with the belt's fly
 * speeds, so the client's own double tap of Space starts creative-style flight; the server only watches the flying flag, charges
 * Mana while it lasts and ends the flight at 0 Mana. See {@link BeltMachine} for the rules and {@link SuspensorSystem} for the adapter.
 */
public final class SuspensorModule implements EquipmentModule {

    public static final String ID = "suspensor";

    private static final String LANG = "server.arrakis.suspensor.";

    private final ModuleAssets.Guard guard;

    public SuspensorModule(JavaPlugin plugin) {
        guard = ModuleAssets.guard(plugin, ID, id -> id.startsWith("Arrakis_Suspensor_"));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        Suspensors.configure(ctx.settings(ID, SuspensorConfig.class), ctx.logger());
        ToggleRegistry.get().register(new ToggleRegistry.Toggle(Suspensors.TOGGLE, 29, "server.customUI.arrakisPicker." + Suspensors.TOGGLE, "Rune_WindStrike",
                SuspensorModule::set,
                uuid -> {
                    BeltMachine machine = Suspensors.peek(uuid);
                    return machine != null && machine.isOn();
                },
                uuid -> {
                    BeltMachine machine = Suspensors.peek(uuid);
                    if (machine != null) {
                        machine.reset();
                    }
                    TestDevices.cancelPush(uuid); // TEST DEVICE: death, respawn, world change: the push ends with a CLEAR
                }));
        ctx.system(new SuspensorSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> Suspensors.forget(event.getPlayerRef().getUuid()));
        // kept for one release: a saved Arrakis_Suspensor_Glider from the glider proof is removed at join (see LegacyGlider)
        ctx.events().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            Holder<EntityStore> holder = event.getHolder();
            if (holder != null && LegacyGlider.removeAll(holder) > 0) {
                Suspensors.logPlain("a saved " + LegacyGlider.ITEM_ID + " (removed asset) was taken out of an inventory at join");
            }
        });
        SuspensorCommands.register(ctx.group("suspensor"), ctx::saveConfig);
    }

    /** The switch hook. ON takes the snapshot and records the wish; the system pushes the settings on its next tick. */
    private static void set(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean on) {
        UUID id = player.getUuid();
        if (!on) {
            BeltMachine machine = Suspensors.peek(id);
            if (machine != null) {
                machine.switchOff();
            }
            TestDevices.cancelPush(id); // TEST DEVICE
            return;
        }
        Player who = store.getComponent(ref, Player.getComponentType());
        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        if (who == null || manager == null) {
            return;
        }
        if (who.getGameMode() != GameMode.Adventure) {
            player.sendMessage(Message.translation(LANG + "creative"));
            return;
        }
        Power power = Power.get();
        float mana = power == null ? Float.MAX_VALUE : power.get(store, ref);
        boolean armed = Suspensors.of(id).switchOn(Suspensors.read(manager.getSettings()), mana, (float) Suspensors.config().resumeMana());
        Suspensors.log(id, player.getUsername(), "state", "belt on, " + (armed ? "armed" : "unarmed (Mana under " + Suspensors.config().resumeMana() + ")"));
        if (armed) {
            player.sendMessage(Message.translation(LANG + "ready"));
        } else {
            Notice.say(player, LANG + "needsMana");
        }
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        lines.add(ToggleRegistry.get().find(Suspensors.TOGGLE) == null ? "PROBLEM the toggle " + Suspensors.TOGGLE + " is not registered" : "toggle " + Suspensors.TOGGLE + " registered");
        lines.add(EntityEffect.getAssetMap().getAsset(SuspensorSystem.HUM_EFFECT) == null ? "PROBLEM effect " + SuspensorSystem.HUM_EFFECT + " is not loaded"
                : "effect " + SuspensorSystem.HUM_EFFECT + " loaded");
        // the glider proof is gone: its item and effect must not be loaded any more
        if (com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(LegacyGlider.ITEM_ID) != null) {
            lines.add("PROBLEM the removed item " + LegacyGlider.ITEM_ID + " is still loaded");
        }
        if (EntityEffect.getAssetMap().getAsset("Arrakis_Suspensor_Falling") != null) {
            lines.add("PROBLEM the removed effect Arrakis_Suspensor_Falling is still loaded");
        }
        SuspensorConfig c = Suspensors.config();
        lines.add("suspensor belt " + c.horizontalSpeed() + "/" + c.verticalSpeed() + " (horizontal/vertical), " + c.manaPerSecond() + " Mana a second, resume at " + c.resumeMana());
        return lines;
    }
}
