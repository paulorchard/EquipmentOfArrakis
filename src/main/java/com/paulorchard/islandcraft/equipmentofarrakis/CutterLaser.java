package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * The line of the Cutter Ray: a white-hot core that tapers from 4 "pixels" wide at the barrel to 1 at the locked face,
 * and a glow around it that pulses in and out, running from the player's barrel to the middle of the locked face.
 *
 * <p>An entity can only be scaled evenly ({@code EntityScaleComponent} is one float), so the line cannot be one box
 * stretched to its length: it would grow thick with it. Instead it is a row of pieces, each an entity of a 1 x 1 x 96
 * unit box ({@code Arrakis_Cutter_Laser}) whose scale is set from the width the line has at that point; a piece is
 * therefore {@link #LENGTH_PER_WIDTH} times as long as it is wide, the pieces get shorter towards the thin end, and they
 * overlap so that no gap shows ({@link BeamMath#pieces}). The glow is a second row of the same pieces, a little wider, in
 * a translucent white ({@code Arrakis_Cutter_Laser_Glow}), whose scale is changed on every tick to make it pulse.
 *
 * <p>They are plain model entities: no collision, never saved ({@code NonSerialized}), not interactable, with no
 * velocity or AI, so the only thing that moves them is this class, once per world tick.
 */
final class CutterLaser {

    static final String CORE_ID = "Arrakis_Cutter_Laser";
    static final String GLOW_ID = "Arrakis_Cutter_Laser_Glow";
    /** One "pixel" of the line, in blocks: the unit the widths below are counted in. */
    static final double PIXEL = 0.003;
    /** Width of the core at the barrel and at the locked face. */
    static final double WIDTH_START = 4 * PIXEL;
    static final double WIDTH_END = 1 * PIXEL;
    /** How far the glow reaches out from the core on each side, at its smallest and its largest, and its pulse rate. */
    static final double GLOW_MIN = 1 * PIXEL;
    static final double GLOW_MAX = 3 * PIXEL;
    static final double PULSE_HZ = 3.0;
    /** The model is 1 unit thick and 96 long at scale 1 (see make_laser.js). */
    static final double LENGTH_PER_WIDTH = 96;
    static final double UNITS_PER_BLOCK = 32;
    /** Each piece starts this share of a length after the one before; the rest overlaps, so there is no gap. */
    static final double ADVANCE = 0.85;
    /** A line shorter than this is not drawn. */
    static final double MIN_LENGTH = 0.15;
    static final int MAX_PIECES = 24;
    /** The barrel in the player's view frame, in blocks: ahead of, to the right of, and below the eye. */
    static final double HAND_FORWARD = 0.9;
    static final double HAND_RIGHT = 0.22;
    static final double HAND_DOWN = 0.32;

    private static Model core;
    private static Model glow;

    private CutterLaser() {
    }

    private static Model load(String id) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(id);
        return asset == null ? null : Model.createUnitScaleModel(asset);
    }

    private static boolean models() {
        if (core == null) {
            core = load(CORE_ID);
        }
        if (glow == null) {
            glow = load(GLOW_ID);
        }
        return core != null && glow != null;
    }

    /** True when both model assets of the line are loaded. */
    static boolean available() {
        return models();
    }

    /** The scale that gives a piece of the model the width {@code blocks}. */
    private static float scale(double blocks) {
        return (float) (blocks * UNITS_PER_BLOCK);
    }

    /** Moves the line to run from the barrel to the locked face centre, adding or removing pieces to fit. */
    static void update(CommandBuffer<EntityStore> buffer, MoltenTrail.State state) {
        if (!models() || state.lock == null) {
            remove(buffer, state);
            return;
        }
        Transform look = TargetUtil.getLook(state.player, buffer);
        Vector3d start = BeamMath.hand(look.getPosition(), new Vector3d(look.getDirection()).normalize(),
                HAND_FORWARD, HAND_RIGHT, HAND_DOWN);
        Vector3d end = state.lock.faceCenter;
        List<BeamMath.Piece> pieces = BeamMath.pieces(start, end, WIDTH_START, WIDTH_END, LENGTH_PER_WIDTH, ADVANCE,
                MIN_LENGTH, MAX_PIECES);
        while (state.segments.size() > pieces.size()) {
            removeOne(buffer, state.segments.remove(state.segments.size() - 1));
        }
        while (state.glows.size() > pieces.size()) {
            removeOne(buffer, state.glows.remove(state.glows.size() - 1));
        }
        if (pieces.isEmpty()) {
            return;
        }
        Rotation3f rotation = Rotation3f.lookAt(new Vector3d(end).sub(start));
        double phase = 0.5 + 0.5 * Math.sin(2 * Math.PI * PULSE_HZ * System.nanoTime() / 1e9);
        double reach = GLOW_MIN + (GLOW_MAX - GLOW_MIN) * phase;
        for (int i = 0; i < pieces.size(); i++) {
            BeamMath.Piece piece = pieces.get(i);
            place(buffer, state.segments, i, core, state, piece.center(), rotation, scale(piece.width()));
            place(buffer, state.glows, i, glow, state, piece.center(), rotation, scale(piece.width() + 2 * reach));
        }
    }

    private static void place(CommandBuffer<EntityStore> buffer, List<Ref<EntityStore>> row, int i, Model model,
                              MoltenTrail.State state, Vector3d center, Rotation3f rotation, float scale) {
        if (i < row.size()) {
            move(buffer, row.get(i), center, rotation, scale);
        } else {
            row.add(buffer.addEntity(holder(buffer.getStore(), model, state.id, center, rotation, scale),
                    AddReason.SPAWN));
        }
    }

    /** Removes every piece of the player's line. */
    static void remove(CommandBuffer<EntityStore> buffer, MoltenTrail.State state) {
        for (Ref<EntityStore> ref : state.segments) {
            removeOne(buffer, ref);
        }
        for (Ref<EntityStore> ref : state.glows) {
            removeOne(buffer, ref);
        }
        state.segments.clear();
        state.glows.clear();
    }

    private static void removeOne(CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref) {
        if (ref != null && ref.isValid()) {
            buffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static void move(CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref, Vector3d center,
                             Rotation3f rotation, float scale) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        // Not teleportPosition/teleportRotation: those are for jumps; a plain move is sent to the clients as a move,
        // which they interpolate.
        TransformComponent transform = buffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            transform.setPosition(center);
            transform.setRotation(rotation);
        }
        HeadRotation head = buffer.getComponent(ref, HeadRotation.getComponentType());
        if (head != null) {
            head.setRotation(rotation);
        }
        EntityScaleComponent size = buffer.getComponent(ref, EntityScaleComponent.getComponentType());
        if (size != null && Math.abs(size.getScale() - scale) > 1e-4f) {
            size.setScale(scale);
        }
    }

    /** The entity of one piece, ready to be added to the store. */
    static Holder<EntityStore> holder(Store<EntityStore> store, Model model, UUID owner, Vector3d center,
                                      Rotation3f rotation, float scale) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(center, rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(BeamSegment.getComponentType(), new BeamSegment(owner));
        holder.ensureComponent(EntityModule.get().getVisibleComponentType());
        // Nothing can hit it, pick it up or be blocked by it, and it is not written to the world save.
        holder.ensureComponent(Intangible.getComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        return holder;
    }

    /** Development: adds one core piece to the store, owned by the given debug owner. Null when the model is missing. */
    static Ref<EntityStore> debugSpawn(Store<EntityStore> store, UUID owner, Vector3d at, Vector3d direction,
                                       float scale) {
        if (!models()) {
            return null;
        }
        return store.addEntity(holder(store, core, owner, at, Rotation3f.lookAt(direction), scale), AddReason.SPAWN);
    }
}
