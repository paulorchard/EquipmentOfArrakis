// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every world tick for every player who has lab state: undoes everything on death, on a world change and on request; prints the
 * watch lines; drives the Mk II / Mk III belt prototype; clamps the fall; and reports a landing after a drop.
 */
final class LabSystem extends EntityTickingSystem<EntityStore> {

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
        LabState.PlayerLab lab = player == null ? null : LabState.peek(player.getUuid());
        if (lab == null) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        MovementManager manager = chunk.getComponent(index, MovementManager.getComponentType());
        MovementStates states = chunk.getComponent(index, MovementStatesComponent.getComponentType()).getMovementStates();
        long now = System.nanoTime();

        // never leave a player stuck: death, another world, or a reset asked for undoes every change
        String world = store.getExternalData().getWorld().getName();
        if (lab.world != null && !lab.world.equals(world)) {
            lab.pendingReset = true;
        }
        lab.world = world;
        if (buffer.getComponent(ref, DeathComponent.getComponentType()) != null || lab.pendingReset) {
            LabMovement.reset(ref, buffer, player, lab);
            LabMovement.say(player, "lab: everything put back (death, world change or reset)");
            return;
        }

        Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        Vector3d position = transform == null ? null : transform.getPosition();
        double clientVy = velocity == null ? Double.NaN : velocity.getClientVelocity().y;
        double posVy = Double.NaN;
        double posVh = Double.NaN;
        if (position != null && lab.lastPos != null && now > lab.lastPosNanos) {
            double seconds = (now - lab.lastPosNanos) / 1e9;
            posVy = (position.y - lab.lastPos.y) / seconds;
            posVh = Math.hypot(position.x - lab.lastPos.x, position.z - lab.lastPos.z) / seconds;
        }
        if (position != null) {
            lab.lastPos = new Vector3d(position);
            lab.lastPosNanos = now;
        }

        flips(player, lab, states);
        if (lab.watchSeconds > 0 && now >= lab.nextWatch) {
            lab.nextWatch = now + (long) (lab.watchSeconds * 1e9);
            watchLine(player, ref, buffer, manager, states, lab, clientVy, posVy, posVh);
        }
        belt(dt, player, ref, buffer, manager, states, lab);
        clamp(velocity, lab, clientVy);
        landing(player, ref, buffer, states, lab, clientVy, position);
    }

    private static void flips(PlayerRef player, LabState.PlayerLab lab, MovementStates states) {
        if (lab.watchSeconds <= 0) {
            return;
        }
        if (lab.lastJumping != null && (lab.lastJumping != states.jumping || lab.lastFlying != states.flying || lab.lastGliding != states.gliding)) {
            log(player, "flip: jumping " + lab.lastJumping + "->" + states.jumping + ", flying " + lab.lastFlying + "->" + states.flying + ", gliding "
                    + lab.lastGliding + "->" + states.gliding);
        }
        lab.lastJumping = states.jumping;
        lab.lastFlying = states.flying;
        lab.lastGliding = states.gliding;
    }

    private static void watchLine(PlayerRef player, Ref<EntityStore> ref, CommandBuffer<EntityStore> buffer, MovementManager manager, MovementStates states,
            LabState.PlayerLab lab, double clientVy, double posVy, double posVh) {
        List<String> flags = new ArrayList<>();
        if (states.jumping) flags.add("jumping");
        if (states.falling) flags.add("falling");
        if (states.fallingFar) flags.add("fallingFar");
        if (states.onGround) flags.add("onGround");
        if (states.flying) flags.add("flying");
        if (states.gliding) flags.add("gliding");
        if (states.crouching) flags.add("crouching");
        if (states.swimming) flags.add("swimming");
        Power power = Power.get();
        Player p = buffer.getComponent(ref, Player.getComponentType());
        MovementSettings settings = manager.getSettings();
        String line = String.format("watch: [%s] vy(client velocity) %.2f vy(position delta) %.2f vh(position delta) %.2f blocks/s, mana %.1f, Z %s, %s, fly %s h %.2f v %.2f, belt mode %d%s",
                String.join(" ", flags), clientVy, posVy, posVh, power == null ? -1f : power.get(buffer, ref), zItem(buffer, ref), p == null ? "?" : p.getGameMode(),
                settings.fly, settings.horizontalFlySpeed, settings.verticalFlySpeed, lab.beltMode, lab.beltOn ? " ACTIVE" : "");
        log(player, line);
    }

    /** The id of the selected Z (utility) item, or "(empty)". */
    static String zItem(com.hypixel.hytale.component.ComponentAccessor<EntityStore> buffer, Ref<EntityStore> ref) {
        InventoryComponent.Utility utility = buffer.getComponent(ref, InventoryComponent.Utility.getComponentType());
        ItemStack item = utility == null ? null : utility.getActiveItem();
        return item == null || item.isEmpty() ? "(empty)" : item.getItemId();
    }

    private void belt(float dt, PlayerRef player, Ref<EntityStore> ref, CommandBuffer<EntityStore> buffer, MovementManager manager, MovementStates states,
            LabState.PlayerLab lab) {
        if (lab.beltMode == 0 && !lab.beltOn) {
            return;
        }
        Power power = Power.get();
        boolean creative = power != null && power.isCreative(buffer, ref);
        boolean hasMana = creative || (power != null && power.get(buffer, ref) > 0f);
        boolean zBelt = LabItems.isBelt(zItem(buffer, ref));
        boolean active = LabRules.beltActive(lab.beltMode, zBelt, states.onGround, states.jumping, hasMana);
        LabConfig config = Labs.config();
        if (active && !lab.beltOn) {
            lab.saved = manager.getSettings().clone();
            float[] speeds = LabRules.flySpeeds(lab.beltMode, (float) config.flySpeed(), (float) config.riseSpeed());
            if (config.beltUsesEngineFly()) {
                manager.getSettings().horizontalFlySpeed = speeds[0];
                manager.getSettings().verticalFlySpeed = speeds[1];
                LabMovement.engineFlying(ref, buffer, true);
            } else {
                LabMovement.setFly(player, manager, FlyMode.Forced, speeds[0], speeds[1]);
            }
            lab.beltOn = true;
            log(player, "belt Mk " + (lab.beltMode == 2 ? "II" : "III") + " ON: " + (config.beltUsesEngineFly() ? "Player.setFlying(true) -> fly " + manager.getSettings().fly : "fly Forced")
                    + ", horizontal " + speeds[0] + ", vertical " + speeds[1]);
        } else if (!active && lab.beltOn) {
            MovementSettings settings = manager.getSettings();
            if (config.beltUsesEngineFly()) {
                LabMovement.engineFlying(ref, buffer, false);
            }
            if (lab.saved != null) {
                settings.fly = lab.saved.fly;
                settings.horizontalFlySpeed = lab.saved.horizontalFlySpeed;
                settings.verticalFlySpeed = lab.saved.verticalFlySpeed;
            }
            LabMovement.push(player, manager);
            LabMovement.sendFlying(player, false);
            LabMovement.stopHum(buffer, ref);
            lab.beltOn = false;
            log(player, "belt OFF: restored fly " + settings.fly + " (jumping " + states.jumping + ", onGround " + states.onGround + ", mana " + hasMana + ", Z belt " + zBelt + ")");
        }
        if (lab.beltOn) {
            LabMovement.startHum(buffer, ref);
            if (!creative && power != null) {
                power.spendUpTo(buffer, ref, LabRules.manaForTick((float) config.manaPerSecond(), dt));
            }
        }
    }

    private static void clamp(Velocity velocity, LabState.PlayerLab lab, double clientVy) {
        if (lab.clampMax <= 0 || velocity == null || Double.isNaN(clientVy) || !LabRules.clampBites(clientVy, lab.clampMax)) {
            return;
        }
        Vector3d client = velocity.getClientVelocity();
        velocity.addInstruction(new Vector3d(client.x, LabRules.clampFall(clientVy, lab.clampMax), client.z), new VelocityConfig(), ChangeVelocityType.Set);
    }

    private static void landing(PlayerRef player, Ref<EntityStore> ref, CommandBuffer<EntityStore> buffer, MovementStates states, LabState.PlayerLab lab,
            double clientVy, Vector3d position) {
        if (!lab.dropTracking) {
            return;
        }
        if (!states.onGround) {
            lab.airborne = true;
            if (!Double.isNaN(clientVy)) {
                lab.minVy = Math.min(lab.minVy, clientVy);
            }
            return;
        }
        if (lab.airborne) {
            float now = health(buffer, ref);
            double maxHealth = maxHealth(buffer, ref);
            log(player, String.format("landed: fastest fall %.2f blocks/s, health %.1f -> %.1f (damage %.1f); the engine formula for that speed gives %d",
                    -lab.minVy, lab.health0, now, lab.health0 - now, LabRules.fallDamage(-lab.minVy, 21.0, maxHealth)));
            lab.dropTracking = false;
            lab.airborne = false;
        }
    }

    static float health(com.hypixel.hytale.component.ComponentAccessor<EntityStore> buffer, Ref<EntityStore> ref) {
        EntityStatMap stats = buffer.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value == null ? -1f : value.get();
    }

    static double maxHealth(com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value == null ? 100.0 : value.getMax();
    }

    /** One line to the player's chat and to the log. */
    static void log(PlayerRef player, String line) {
        LabMovement.say(player, "lab " + line);
        Labs.log("[" + player.getUsername() + "] " + line);
    }

}
