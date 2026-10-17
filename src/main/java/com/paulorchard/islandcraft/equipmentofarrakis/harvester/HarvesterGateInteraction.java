package com.paulorchard.islandcraft.equipmentofarrakis.harvester;

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
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import com.paulorchard.islandcraft.equipmentofarrakis.power.ManaRefusal;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Harvester_Gate}: first in the Dew Harvester Primary chain, before the swing. It decides, in this
 * order, and ends the chain as Failed (so the swing never starts) with a short message when it must: the block under the crosshair
 * within {@code Range} is not the Primrose; no LitreJon is carried; every LitreJon is full; less than {@code ManaPerSwing} Mana.
 * Otherwise it accepts the click: pays the Mana (creative pays none), starts the hum and remembers the swing for
 * {@link HarvesterFinishInteraction}, which moves the water when the swing is over.
 *
 * <pre>
 * {"Type": "Arrakis_Harvester_Gate", "Range": 3, "ManaPerSwing": 10, "WaterPerSwing": 10, "PrimroseBlockId": "Arrakis_Primrose",
 *  "HumEffectId": "Arrakis_Harvester_Active", "FlaskItemId": "Arrakis_Tool_Litrejon"}
 * </pre>
 */
public class HarvesterGateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Harvester_Gate";

    public static final BuilderCodec<HarvesterGateInteraction> CODEC = BuilderCodec
            .builder(HarvesterGateInteraction.class, HarvesterGateInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("ManaPerSwing", Codec.FLOAT, false), (i, v) -> i.manaPerSwing = v, i -> i.manaPerSwing)
            .add()
            .append(new KeyedCodec<>("WaterPerSwing", Codec.INTEGER, false), (i, v) -> i.waterPerSwing = v, i -> i.waterPerSwing)
            .add()
            .append(new KeyedCodec<>("PrimroseBlockId", Codec.STRING, false), (i, v) -> i.primroseBlockId = v, i -> i.primroseBlockId)
            .add()
            .append(new KeyedCodec<>("HumEffectId", Codec.STRING, false), (i, v) -> i.humEffectId = v, i -> i.humEffectId)
            .add()
            .append(new KeyedCodec<>("FlaskItemId", Codec.STRING, false), (i, v) -> i.flaskItemId = v, i -> i.flaskItemId)
            .add()
            .build();

    private float range = 3f;
    private float manaPerSwing = 10f;
    private int waterPerSwing = 10;
    private String primroseBlockId = "Arrakis_Primrose";
    private String humEffectId = "Arrakis_Harvester_Active";
    private String flaskItemId = "Arrakis_Tool_Litrejon";

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        Power power = Power.get();
        if (player == null || power == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = buffer.getExternalData().getWorld();
        Vector3i target = TargetUtil.getTargetBlock(ref, range, buffer);
        BlockType block = target == null ? null : new WorldAccess(world).blockType(target.x, target.y, target.z);
        boolean primrose = HarvesterRules.isPrimrose(block == null ? null : block.getId(), primroseBlockId);

        ContainerItem flask = ContainerInventory.find(flaskItemId);
        int carried = flask == null ? 0 : ContainerInventory.carried(buffer, ref, flask).size();
        boolean room = flask != null && ContainerInventory.firstWithRoom(buffer, ref, flask) != null;
        boolean creative = power.isCreative(buffer, ref);
        HarvesterRules.Refusal refusal = HarvesterRules.refusal(primrose, carried, room, power.get(buffer, ref), manaPerSwing, creative);
        if (refusal != HarvesterRules.Refusal.NONE) {
            switch (refusal) {
                case NOT_PRIMROSE -> Notice.say(player, "server.arrakis.harvester.noPrimrose");
                case NO_FLASK -> Notice.say(player, "server.arrakis.harvester.noFlask");
                case FLASKS_FULL -> Notice.say(player, "server.arrakis.harvester.flasksFull");
                default -> ManaRefusal.say(player, manaPerSwing);
            }
            context.getState().state = InteractionState.Failed;
            return;
        }
        // Accepted: pay now (no refund if the swing is cut short), start the hum, remember the swing.
        if (!power.spend(buffer, ref, manaPerSwing)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        HarvesterState.startHum(buffer, ref, humEffectId);
        HarvesterState.put(player.getUuid(), new HarvesterState.Pending(new Vector3i(target), primroseBlockId, waterPerSwing, humEffectId));
    }
}
