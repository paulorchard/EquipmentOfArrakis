package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

import java.util.List;
import java.util.UUID;

/**
 * The adapter of {@link BeltMachine}: every world tick, for a player who has a belt, reads the flying flag, the game mode, Mana and the
 * movement settings, asks the machine what to do and does it. A player without a belt costs one map lookup.
 */
final class SuspensorSystem extends EntityTickingSystem<EntityStore> {

    static final String HUM_EFFECT = "Arrakis_Suspensor_Hum";

    private Query<EntityStore> query;

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType(), MovementManager.getComponentType(),
                    MovementStatesComponent.getComponentType());
        }
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        UUID id = player.getUuid();
        BeltMachine machine = Suspensors.peek(id);
        if (machine == null && !TestDevices.any(id)) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player who = chunk.getComponent(index, Player.getComponentType());
        MovementManager manager = chunk.getComponent(index, MovementManager.getComponentType());
        MovementStatesComponent statesComponent = chunk.getComponent(index, MovementStatesComponent.getComponentType());
        if (who == null || manager == null || statesComponent == null) {
            return;
        }
        MovementStates states = statesComponent.getMovementStates();
        TestDevices.tick(dt, index, chunk, buffer, player, ref, states, machine); // TEST DEVICE: the probe and the push test; the belt below is unchanged
        if (machine == null) {
            return;
        }
        SuspensorConfig config = Suspensors.config();
        Power power = Power.get();
        float mana = power == null ? Float.MAX_VALUE : power.get(buffer, ref);
        Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
        double clientVy = velocity == null ? 0 : velocity.getClientVelocity().y;
        List<BeltMachine.Act> acts = machine.tick(dt, states.flying, who.getGameMode() == GameMode.Adventure, mana, (float) config.manaPerSecond(),
                (float) config.resumeMana(), Suspensors.read(manager.getSettings()), Suspensors.speeds(), Suspensors.gate(),
                new BeltMachine.Ground(states.jumping, states.onGround, System.currentTimeMillis(), buffer.getExternalData().getWorld().getTick(), clientVy), Suspensors.descent());
        for (BeltMachine.Act act : acts) {
            carryOut(act, player, who, manager, ref, buffer, power, config, velocity);
        }
        Suspensors.dropIfIdle(id);
    }

    private static void carryOut(BeltMachine.Act act, PlayerRef player, Player who, MovementManager manager, Ref<EntityStore> ref,
                                 CommandBuffer<EntityStore> buffer, Power power, SuspensorConfig config, Velocity velocity) {
        String name = player.getUsername();
        switch (act.kind()) {
            case PUSH -> {
                Suspensors.write(manager.getSettings(), act.settings());
                manager.update(player.getPacketHandler());
                Suspensors.log(player.getUuid(), name, "push", "pushed fly " + act.settings().mode() + " h " + act.settings().horizontal() + " v " + act.settings().vertical());
            }
            case RESTORE -> {
                Suspensors.write(manager.getSettings(), act.settings());
                manager.update(player.getPacketHandler());
                Suspensors.log(player.getUuid(), name, "off", "belt off: restored the snapshot (fly " + act.settings().mode() + " h " + act.settings().horizontal()
                        + " v " + act.settings().vertical() + ")");
            }
            case RESET -> {
                manager.resetDefaultsAndUpdate(ref, buffer);
                Suspensors.log(player.getUuid(), name, "off", "belt off: the snapshot is not trusted, movement settings reset to the defaults");
            }
            case STOP_FLYING -> who.setFlying(ref, false, buffer);
            case PUSH_DESCENT -> {
                if (velocity != null) {
                    velocity.addInstruction(new Vector3d(0.0, act.amount(), 0.0), Suspensors.noDecay(), ChangeVelocityType.Set);
                }
            }
            case CLEAR_PUSH -> {
                if (velocity != null) {
                    velocity.addInstruction(new Vector3d(0.0, 0.0, 0.0), new VelocityConfig(), ChangeVelocityType.Set);
                }
            }
            case SPEND -> {
                if (power != null) {
                    power.spendUpTo(buffer, ref, act.amount());
                }
            }
            case HUM -> {
                if (config.hum()) {
                    EffectControllerComponent controller = buffer.getComponent(ref, EffectControllerComponent.getComponentType());
                    EntityEffect effect = EntityEffect.getAssetMap().getAsset(HUM_EFFECT);
                    if (controller != null && effect != null) {
                        controller.addEffect(ref, effect, buffer);
                    }
                }
            }
            case HUM_OFF -> {
                EffectControllerComponent controller = buffer.getComponent(ref, EffectControllerComponent.getComponentType());
                int effect = EntityEffect.getAssetMap().getIndex(HUM_EFFECT);
                if (controller != null && effect >= 0) {
                    controller.removeEffect(ref, effect, buffer);
                }
            }
            case NEEDS_MANA -> Notice.say(player, "server.arrakis.suspensor.needsMana");
            case LOG -> {
                if (act.text().startsWith("gate ") || act.text().startsWith("descent")) {
                    Suspensors.logGate(name, act.text());
                } else {
                    Suspensors.log(player.getUuid(), name, act.text().substring(0, Math.min(14, act.text().length())), act.text());
                }
            }
        }
    }
}
