package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Interaction type {@code Arrakis_Extractor_Gate}: first in the Blood Extractor Primary chain. Finds the dead mob in range and aim and
 * decides, in this order, ending the chain as Failed (no animation, nothing spent) with a short message when it must: no corpse
 * in range and aim; the corpse already used; (only with {@code RequireCrouched}, the fallback when the crouch cannot be forced) not
 * crouching; no Blood Bag carried; every bag full. Otherwise it claims the corpse (one body, one use), forces the crouch and
 * remembers the poke for {@link ExtractorFinishInteraction}. No Mana is involved.
 *
 * <p>What counts as a corpse is data-light: a non-player entity with a {@code DeathComponent} (and, with {@code RequireNpc}, an NPC).
 * It is found by its aim point, {@code AimHeight} above its feet, being within {@code AimRadius} of the look ray and {@code Range} along it.
 */
public class ExtractorGateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Extractor_Gate";

    public static final BuilderCodec<ExtractorGateInteraction> CODEC = BuilderCodec
            .builder(ExtractorGateInteraction.class, ExtractorGateInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("AimRadius", Codec.FLOAT, false), (i, v) -> i.aimRadius = v, i -> i.aimRadius)
            .add()
            .append(new KeyedCodec<>("AimHeight", Codec.FLOAT, false), (i, v) -> i.aimHeight = v, i -> i.aimHeight)
            .add()
            .append(new KeyedCodec<>("PokeSeconds", Codec.FLOAT, false), (i, v) -> i.pokeSeconds = v, i -> i.pokeSeconds)
            .add()
            .append(new KeyedCodec<>("BloodPerBody", Codec.INTEGER, false), (i, v) -> i.bloodPerBody = v, i -> i.bloodPerBody)
            .add()
            .append(new KeyedCodec<>("ForceCrouch", Codec.BOOLEAN, false), (i, v) -> i.forceCrouch = v, i -> i.forceCrouch)
            .add()
            .append(new KeyedCodec<>("RequireCrouched", Codec.BOOLEAN, false), (i, v) -> i.requireCrouched = v, i -> i.requireCrouched)
            .add()
            .append(new KeyedCodec<>("RequireNpc", Codec.BOOLEAN, false), (i, v) -> i.requireNpc = v, i -> i.requireNpc)
            .add()
            .append(new KeyedCodec<>("BagItemId", Codec.STRING, false), (i, v) -> i.bagItemId = v, i -> i.bagItemId)
            .add()
            .build();

    private float range = 2.5f;
    private float aimRadius = 0.9f;
    private float aimHeight = 0.5f;
    private float pokeSeconds = 1.0f;
    private int bloodPerBody = 10;
    private boolean forceCrouch = true;
    private boolean requireCrouched = false;
    private boolean requireNpc = true;
    private String bagItemId = "Arrakis_Blood_Bag";

    /** How long the poke lasts, seconds: the run time of the poke interaction follows it. */
    float pokeSeconds() {
        return pokeSeconds;
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> corpse = findCorpse(buffer, ref);
        boolean used = corpse != null && ExtractorState.isUsed(corpse);
        ContainerItem bag = ContainerInventory.find(bagItemId);
        int bags = bag == null ? 0 : ContainerInventory.carried(buffer, ref, bag).size();
        boolean room = bag != null && ContainerInventory.firstWithRoom(buffer, ref, bag) != null;
        boolean crouched = ExtractorState.isCrouching(buffer, ref);
        ExtractorRules.Refusal refusal =
                ExtractorRules.refusal(corpse != null, used, requireCrouched && !forceCrouch, crouched, bags, room);
        if (refusal != ExtractorRules.Refusal.NONE) {
            Notice.say(player, switch (refusal) {
                case NO_CORPSE -> "server.arrakis.extractor.noCorpse";
                case ALREADY_USED -> "server.arrakis.extractor.used";
                case NOT_CROUCHING -> "server.arrakis.extractor.needCrouch";
                case NO_BAG -> "server.arrakis.extractor.noBag";
                default -> "server.arrakis.extractor.bagsFull";
            });
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!ExtractorState.claim(corpse)) {
            Notice.say(player, "server.arrakis.extractor.used");
            context.getState().state = InteractionState.Failed;
            return;
        }
        long end = System.nanoTime() + (long) (pokeSeconds * 1_000_000_000L);
        ExtractorState.put(player.getUuid(), new ExtractorState.Pending(corpse, end, crouched, bloodPerBody, forceCrouch));
        if (forceCrouch) {
            ExtractorState.forceCrouch(buffer, ref);
        }
    }

    /** The nearest corpse the look ray hits within Range, or null. */
    private Ref<EntityStore> findCorpse(CommandBuffer<EntityStore> buffer, Ref<EntityStore> self) {
        Transform look = TargetUtil.getLook(self, buffer);
        Vector3d origin = new Vector3d(look.getPosition());
        Vector3d dir = new Vector3d(look.getDirection());
        List<Ref<EntityStore>> near = TargetUtil.getAllEntitiesInSphere(origin, range + aimRadius, buffer);
        List<Ref<EntityStore>> refs = new ArrayList<>();
        List<CorpseAim.Candidate> candidates = new ArrayList<>();
        for (Ref<EntityStore> entity : near) {
            if (entity.equals(self) || !entity.isValid()) {
                continue;
            }
            boolean isPlayer = buffer.getComponent(entity, Player.getComponentType()) != null;
            boolean dead = buffer.getComponent(entity, DeathComponent.getComponentType()) != null;
            boolean npc = buffer.getComponent(entity, NPCEntity.getComponentType()) != null;
            TransformComponent transform = buffer.getComponent(entity, TransformComponent.getComponentType());
            if (transform == null || !CorpseAim.isCorpse(isPlayer, dead, npc, requireNpc)) {
                continue;
            }
            candidates.add(new CorpseAim.Candidate(refs.size(), new Vector3d(transform.getPosition()).add(0, aimHeight, 0)));
            refs.add(entity);
        }
        int pick = CorpseAim.pick(origin, dir, candidates, range, aimRadius);
        return pick < 0 ? null : refs.get(pick);
    }
}
