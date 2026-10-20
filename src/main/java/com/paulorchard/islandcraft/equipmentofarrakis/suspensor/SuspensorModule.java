package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
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
 * The Suspensor Belt: two toggles on the picker page (Mk III rises and sinks at a low speed, Mk II flies flat), of one exclusive
 * group. While a mark is on, the movement setting {@code fly} is Allowed with the mark's fly speeds, so the client's own double tap
 * of Space starts creative-style flight; the server only watches the flying flag, charges Mana while it lasts and ends the flight at
 * 0 Mana. See {@link BeltMachine} for the rules and {@link SuspensorSystem} for the adapter.
 */
public final class SuspensorModule implements EquipmentModule {

    public static final String ID = "suspensor";
    public static final String GROUP = "suspensor";

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
        if (Suspensors.config().mk1().listed()) {
            register(Suspensors.TOGGLE_I_FAR, 29, BeltMachine.Mark.I_FAR);
            register(Suspensors.TOGGLE_I_SPEED, 30, BeltMachine.Mark.I_SPEED);
        }
        register(Suspensors.TOGGLE_III, 31, BeltMachine.Mark.III);
        register(Suspensors.TOGGLE_II, 32, BeltMachine.Mark.II);
        ctx.system(new SuspensorSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> Suspensors.forget(event.getPlayerRef().getUuid()));
        SuspensorCommands.register(ctx.group("suspensor"), ctx::saveConfig);
    }

    private static void register(String id, int order, BeltMachine.Mark mark) {
        ToggleRegistry.get().register(new ToggleRegistry.Toggle(id, order, "server.customUI.arrakisPicker." + id, "Rune_WindStrike",
                (store, ref, player, on) -> set(store, ref, player, mark, on),
                uuid -> {
                    BeltMachine machine = Suspensors.peek(uuid);
                    return machine != null && machine.isOn(mark);
                },
                uuid -> {
                    BeltMachine machine = Suspensors.peek(uuid);
                    if (machine != null) {
                        machine.reset();
                    }
                }, GROUP));
    }

    /** The switch hook of both marks. ON takes the snapshot and records the wish; the system pushes the settings on its next tick. */
    private static void set(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, BeltMachine.Mark mark, boolean on) {
        UUID id = player.getUuid();
        if (!on) {
            BeltMachine machine = Suspensors.peek(id);
            if (machine != null && machine.isOn(mark)) {
                machine.switchOff();
            }
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
        boolean armed = Suspensors.of(id).switchOn(mark, Suspensors.read(manager.getSettings()), mana, (float) Suspensors.config().resumeMana());
        Suspensors.log(id, player.getUsername(), "state", "Mk " + mark + " on, " + (armed ? "armed" : "unarmed (Mana under " + Suspensors.config().resumeMana() + ")"));
        if (armed) {
            player.sendMessage(Message.translation(LANG + switch (mark) {
                case III -> "ready3";
                case II -> "ready2";
                default -> "ready1";
            }));
        } else {
            Notice.say(player, LANG + "needsMana");
        }
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        for (String id : new String[] {Suspensors.TOGGLE_III, Suspensors.TOGGLE_II}) {
            lines.add(ToggleRegistry.get().find(id) == null ? "PROBLEM the toggle " + id + " is not registered" : "toggle " + id + " registered");
        }
        boolean listed = Suspensors.config().mk1().listed();
        for (String id : new String[] {Suspensors.TOGGLE_I_FAR, Suspensors.TOGGLE_I_SPEED}) {
            boolean there = ToggleRegistry.get().find(id) != null;
            lines.add(there == listed ? "toggle " + id + (there ? " registered" : " not listed (Mk1.Listed false)") : "PROBLEM the toggle " + id + " is " + (there ? "registered" : "missing"));
        }
        Mk1Config m = Suspensors.config().mk1();
        lines.add("suspensor Mk I route " + m.route() + ", descent " + m.descentSpeed() + ", speed threshold " + m.speedThreshold() + " x " + m.speedTicks() + " ticks, "
                + (m.massField() ? "mass" : "drag") + " factor " + m.dragFactor() + ", " + m.manaPerSecond() + " Mana a second");
        lines.add(EntityEffect.getAssetMap().getAsset(SuspensorSystem.HUM_EFFECT) == null ? "PROBLEM effect " + SuspensorSystem.HUM_EFFECT + " is not loaded"
                : "effect " + SuspensorSystem.HUM_EFFECT + " loaded");
        SuspensorConfig c = Suspensors.config();
        lines.add("suspensor Mk III " + c.mk3().horizontalSpeed() + "/" + c.mk3().verticalSpeed() + ", Mk II " + c.mk2().horizontalSpeed() + "/"
                + c.mk2().verticalSpeed() + ", " + c.manaPerSecond() + " Mana a second, resume at " + c.resumeMana());
        return lines;
    }
}
