package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Dew_Reaper_Gate}: the first interaction of the Dew Reaper's right click, before the slow swing. It
 * decides, in this order, and ends the chain as Failed (so the swing animation never starts, as with the Cutter Ray's gate) with a
 * chat message when it must:
 * <ol>
 *   <li>Water of Arrakis is not there: "Water of Arrakis is needed";</li>
 *   <li>less than {@code PowerCost} power (the attempt still counts as use of power for the recharge).</li>
 * </ol>
 * The water goes into a Litrejon, never into the player's own water: whether there is a flask with room is not a reason to refuse, it is
 * looked at when the swing ends (and if there is none the water is lost, with a message).
  * Otherwise it accepts the click: pays {@code PowerCost} (the stat's whole 100 by default), starts the hum effect, and remembers what
 * the swing is for ({@link DewReaperState.Pending}); the swing then runs, and {@link DewReaperFinishInteraction} does the work when it
 * is over. A swing that is cut short never gets there, so no water comes and no power is given back.
 *
 * <p>The target is the first block within {@code Range} under the crosshair ({@code TargetUtil.getTargetBlock}, which counts any block
 * that is not air, whatever its hitbox, so the whole cell of a Primrose block is hit). Primrose mode when that block's id is
 * {@code PrimroseBlockId}.
 *
 * <pre>
 * {"Type": "Arrakis_Dew_Reaper_Gate", "Range": 3, "PowerCost": 100, "PrimroseBlockId": "Arrakis_Primrose", "PrimroseWater": 50,
 *  "AirWaterUnits": 1, "ShowMessages": true, "HumEffectId": "Arrakis_Dew_Reaper_Active"}
 * </pre>
 */
public class DewReaperGateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Dew_Reaper_Gate";

    public static final BuilderCodec<DewReaperGateInteraction> CODEC = BuilderCodec
            .builder(DewReaperGateInteraction.class, DewReaperGateInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("PowerCost", Codec.FLOAT, false), (i, v) -> i.powerCost = v, i -> i.powerCost)
            .add()
            .append(new KeyedCodec<>("PrimroseBlockId", Codec.STRING, false), (i, v) -> i.primroseBlockId = v, i -> i.primroseBlockId)
            .add()
            .append(new KeyedCodec<>("PrimroseWater", Codec.FLOAT, false), (i, v) -> i.primroseWater = v, i -> i.primroseWater)
            .add()
            .append(new KeyedCodec<>("AirWaterUnits", Codec.FLOAT, false), (i, v) -> i.airWaterUnits = v, i -> i.airWaterUnits)
            .add()
            .append(new KeyedCodec<>("ShowMessages", Codec.BOOLEAN, false), (i, v) -> i.showMessages = v, i -> i.showMessages)
            .add()
            .append(new KeyedCodec<>("HumEffectId", Codec.STRING, false), (i, v) -> i.humEffectId = v, i -> i.humEffectId)
            .add()
            .build();

    private float range = 3f;
    private float powerCost = 100f;
    private String primroseBlockId = "Arrakis_Primrose";
    private float primroseWater = 50f;
    private float airWaterUnits = 1f;
    private boolean showMessages = true;
    private String humEffectId = "Arrakis_Dew_Reaper_Active";

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = buffer.getExternalData().getWorld();
        Vector3i target = TargetUtil.getTargetBlock(ref, range, buffer);
        BlockType block = target == null ? null : world.getBlockType(target);
        DewReaperRules.Mode mode = DewReaperRules.mode(block == null ? null : block.getId(), primroseBlockId);

        WaterApi water = WaterLink.api();
        DewReaperRules.Refusal refusal = DewReaperRules.refusal(water != null, ArrakisPower.get(buffer, ref), powerCost);
        if (refusal != DewReaperRules.Refusal.NONE) {
            if (refusal == DewReaperRules.Refusal.NO_POWER) {
                // Trying counts as use, as for the other tools, so that the pack does not recharge under a held click.
                ArrakisPower.spend(buffer, ref, 0f);
            }
            DewReaperState.say(buffer, ref, showMessages, switch (refusal) {
                case WATER_MOD_MISSING -> "needsWaterMod";
                default -> "needsPower";
            }, "cost", Math.round(powerCost));
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Accepted: pay now (no refund if the swing is cut short), start the hum, remember what the swing is for.
        ArrakisPower.spend(buffer, ref, powerCost);
        DewReaperState.startHum(buffer, ref, humEffectId);
        DewReaperState.put(player.getUuid(), new DewReaperState.Pending(mode, target == null ? null : new Vector3i(target),
                primroseBlockId, primroseWater, airWaterUnits, showMessages, humEffectId, System.nanoTime()));
    }
}
