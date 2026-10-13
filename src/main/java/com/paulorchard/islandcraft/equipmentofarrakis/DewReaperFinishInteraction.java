package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Dew_Reaper_Finish}: the last interaction of the Dew Reaper's right click, after the slow swing. It
 * is only reached when the swing ran to its end (a swing that is cut short ends the chain before it), and then collects the water the
 * click was accepted for, by what {@link DewReaperGateInteraction} remembered:
 * <ul>
 *   <li>Primrose: {@code PrimroseWater} units, if the Primrose is still where it was (the block is not touched).</li>
 *   <li>Anywhere else: {@code AirWaterUnits} units.</li>
 * </ul>
 * The units go into the first Litrejon with room, looked up now (hotbar, then storage, then backpack) and written back to the same
 * slot, up to its capacity. They never go to the player's own water. Whatever does not fit, or all of it when there is no Litrejon or
 * every one is full, is lost, with the message "{n} water lost, no container". The hum is taken off here too. It charges nothing.
 */
public class DewReaperFinishInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Dew_Reaper_Finish";

    public static final BuilderCodec<DewReaperFinishInteraction> CODEC = BuilderCodec
            .builder(DewReaperFinishInteraction.class, DewReaperFinishInteraction::new, SimpleInstantInteraction.CODEC).build();

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        DewReaperState.Pending pending = DewReaperState.take(player.getUuid());
        if (pending == null) {
            return;
        }
        DewReaperState.stopHum(buffer, ref, pending.humEffectId());
        WaterApi water = WaterLink.api();
        if (water == null) {
            return;
        }
        if (pending.mode() == DewReaperRules.Mode.PRIMROSE && !primroseStillThere(buffer, pending)) {
            return;
        }
        double units = DewReaperRules.units(pending.mode(), pending.primroseWater(), pending.airUnits());
        DewReaperState.Flask flask = DewReaperState.findFlask(buffer, ref, water);
        if (flask == null) {
            DewReaperState.say(buffer, ref, pending.showMessages(), "lost", "lost", Math.round(units));
            return;
        }
        double was = water.amount(flask.stack());
        double now = DewReaperRules.filled(was, units, water.capacity());
        flask.container().setItemStackForSlot(flask.slot(), water.withAmount(flask.stack(), now));
        DewReaperState.say(buffer, ref, pending.showMessages(), "filledFlask", "added", Math.round(now - was), "amount", Math.round(now),
                "capacity", Math.round(water.capacity()));
        double lost = units - (now - was);
        if (lost >= 0.5) {
            DewReaperState.say(buffer, ref, pending.showMessages(), "lost", "lost", Math.round(lost));
        }
    }

    private static boolean primroseStillThere(CommandBuffer<EntityStore> buffer, DewReaperState.Pending pending) {
        Vector3i at = pending.primrose();
        BlockType block = at == null ? null : new WorldAccess(buffer.getExternalData().getWorld()).blockType(at.x, at.y, at.z);
        return block != null && pending.primroseBlockId().equals(block.getId());
    }
}
