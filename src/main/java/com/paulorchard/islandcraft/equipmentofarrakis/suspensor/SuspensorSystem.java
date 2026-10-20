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
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

import org.joml.Vector3d;

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
        FallProbe probe = Suspensors.probe(id);
        if (machine == null && probe == null) {
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
        Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        double vy = velocity == null ? 0.0 : velocity.getClientVelocity().y;
        double y = transform == null ? 0.0 : transform.getPosition().y;
        if (probe != null) {
            for (String line : probe.observe(states.falling, states.fallingFar, states.onGround, y, vy, System.currentTimeMillis(), machine != null && machine.engaged())) {
                Suspensors.probeLine(player, probe, line);
            }
        }
        if (machine == null) {
            return;
        }
        SuspensorConfig config = Suspensors.config();
        Power power = Power.get();
        float mana = power == null ? Float.MAX_VALUE : power.get(buffer, ref);
        MovementSettings settings = manager.getSettings();
        BeltMachine.Sense sense = new BeltMachine.Sense(dt, states.flying, who.getGameMode() == GameMode.Adventure, states.falling, states.fallingFar,
                states.onGround, states.inFluid || states.swimming, vy, y, health(buffer, ref), mana, Suspensors.read(settings),
                new BeltMachine.Physics(settings.mass, settings.dragCoefficient));
        List<BeltMachine.Act> acts = machine.tick(sense, (float) config.manaPerSecond(), (float) config.resumeMana(), config.mk1().rules(), Suspensors::speeds);
        for (BeltMachine.Act act : acts) {
            carryOut(act, player, who, manager, ref, buffer, power, config, velocity);
        }
        Suspensors.dropIfIdle(id);
    }

    static float health(com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value == null ? -1f : value.get();
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
            case DESCENT_ON, DESCENT_OFF -> {
                MovementSettings settings = manager.getSettings();
                settings.mass = act.physics().mass();
                settings.dragCoefficient = act.physics().drag();
                manager.update(player.getPacketHandler());
                Suspensors.log(player.getUuid(), name, act.kind() == BeltMachine.Kind.DESCENT_ON ? "descent" : "descent-off", "pushed mass " + settings.mass
                        + " drag " + settings.dragCoefficient + (act.kind() == BeltMachine.Kind.DESCENT_ON ? " (descent on)" : " (descent off: back to the snapshot)"));
            }
            case VELOCITY -> {
                if (velocity != null) {
                    Vector3d client = velocity.getClientVelocity();
                    velocity.addInstruction(new Vector3d(client.x, act.amount(), client.z), new VelocityConfig(), ChangeVelocityType.Set);
                }
            }
            case NEEDS_MANA -> Notice.say(player, "server.arrakis.suspensor.needsMana");
            case LOG -> Suspensors.log(player.getUuid(), name, act.text().substring(0, Math.min(14, act.text().length())), act.text());
        }
    }
}
