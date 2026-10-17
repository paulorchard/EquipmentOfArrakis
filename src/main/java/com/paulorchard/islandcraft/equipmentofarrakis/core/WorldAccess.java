package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthModule;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Block, height and chunk access for a loaded world on Hytale 0.7.0, where chunks are ECS columns plus sections and World lost
 * getBlock, setBlock and getChunkAsync. Adapted from a reference that was compiled against the 0.7.0 jar; what it leaves out
 * (the pre-load path of world generation, fluids) this mod does not need.
 *
 * <p>Thread rule: Store.getComponent is for the world thread, so use an instance from an interaction, a system tick, a command
 * or world.execute(...). Create one per operation: it caches sections while it lives, so never keep it. Async continuations do
 * not run on the world thread; the loaders below hop back with thenApplyAsync(x -> x, world).
 */
public final class WorldAccess {

    /** What a height read returns when the column has no surface or is not loaded. */
    public static final int NO_HEIGHT = Integer.MIN_VALUE;

    private final ChunkStore chunks;
    private final SectionReader sections;

    public WorldAccess(World world) {
        this.chunks = world.getChunkStore();
        this.sections = new SectionReader(chunks);
    }

    // ---- is it loaded?

    /** The chunk column's entity ref, or null when that chunk column is not loaded. */
    public Ref<ChunkStore> column(int x, int z) {
        Ref<ChunkStore> ref = chunks.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        return ref != null && ref.isValid() ? ref : null;
    }

    public boolean isLoaded(int x, int z) {
        return column(x, z) != null;
    }

    /**
     * True when the chunk column is loaded AND ticking: the old World.getChunkIfLoaded(index) != null meant this (ChunkFlag.TICKING
     * is gone). In 0.7.0 a chunk is ticking when its archetype does not contain the NonTicking marker; vanilla /chunk info
     * computes it the same way. World thread only.
     */
    public boolean isTicking(int x, int z) {
        Ref<ChunkStore> ref = column(x, z);
        return ref != null && !chunks.getStore().getArchetype(ref).contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }

    /** The section containing this block, or null when it is not loaded. */
    public Ref<ChunkStore> sectionRef(int x, int y, int z) {
        Ref<ChunkStore> ref = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        return ref != null && ref.isValid() ? ref : null;
    }

    // ---- reads

    /** Block id at a world position; 0 (air) when the section is not loaded. */
    public int block(int x, int y, int z) {
        return sections.getBlock(x, y, z);
    }

    /** Null for air, an unknown id, or a section that is not loaded. */
    public BlockType blockType(int x, int y, int z) {
        return blockTypeOf(block(x, y, z));
    }

    public static BlockType blockTypeOf(int blockId) {
        return blockId <= 0 ? null : BlockType.getAssetMap().getAsset(blockId);
    }

    /**
     * Y of the top block of the column at WORLD x/z, or NO_HEIGHT when the column is not loaded or has no surface. The heightmap
     * skips blocks that neither collide nor are opaque (plants, decor).
     */
    public int height(int x, int z) {
        Ref<ChunkStore> column = column(x, z);
        if (column == null) {
            return NO_HEIGHT;
        }
        HeightmapColumn map = chunks.getStore().getComponentConcurrent(column, HeightmapColumn.getComponentType());
        return map == null ? NO_HEIGHT : map.getHeight(x, z);
    }

    // ---- writes

    /**
     * Sets the block at a world position, rotation 0 and filler 0; false when its section is not loaded or the name is unknown.
     * {@code settings} are SetBlockSettings flags (0 = the engine's normal behaviour). The name "Empty" removes the block.
     */
    public boolean setBlock(int x, int y, int z, String blockName, int settings) {
        Ref<ChunkStore> section = sectionRef(x, y, z);
        if (section == null) {
            return false;
        }
        int id = BlockType.getAssetMap().getIndex(blockName);
        BlockType type = id == Integer.MIN_VALUE ? null : BlockType.getAssetMap().getAsset(id);
        if (type == null) {
            return false;
        }
        return BlockOperations.setBlock(chunks, section, x, y, z, id, type, 0, 0, settings);
    }

    /** The block health of the section containing this position, or null when the section is not loaded. */
    public BlockHealthSection healthSection(int x, int y, int z) {
        Ref<ChunkStore> section = sectionRef(x, y, z);
        return section == null ? null
                : chunks.getStore().getComponent(section, BlockHealthModule.get().getBlockHealthSectionComponentType());
    }

    /** The ChunkSection (save mark, keep-loaded counter) containing this position, or null when it is not loaded. */
    public ChunkSection chunkSection(int x, int y, int z) {
        Ref<ChunkStore> section = sectionRef(x, y, z);
        return section == null ? null : chunks.getStore().getComponent(section, ChunkSection.getComponentType());
    }

    public ChunkStore chunkStore() {
        return chunks;
    }

    // ---- loading

    /**
     * Loads a chunk column, ticking or not. The raw ChunkStore future does not complete on the world thread, so this hops back
     * with thenApplyAsync(..., world). The ref is null if the chunk could not be loaded; callers still check isValid().
     */
    public static CompletableFuture<Ref<ChunkStore>> loadColumn(World world, int chunkX, int chunkZ, boolean ticking) {
        return world.getChunkStore()
                .getChunkReferenceAsync(ChunkUtil.indexChunk(chunkX, chunkZ), ticking ? GetChunkFlags.SET_TICKING : 0)
                .thenApplyAsync(ref -> ref, world);
    }

    /** Loads (or creates) the section containing a world position; completes on the world thread with its ref. */
    public static CompletableFuture<Ref<ChunkStore>> loadSection(World world, int x, int y, int z) {
        return world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x, y, z).thenApplyAsync(ref -> ref, world);
    }

    /** Sections a development test holds loaded; {@link #release} lets them go. World thread only. */
    public static final class Held {
        private final List<ChunkSection> sections = new ArrayList<>();
        /** How many sections were asked for. */
        public int wanted;

        public int loaded() {
            return sections.size();
        }

        public void release() {
            for (ChunkSection section : sections) {
                section.removeKeepLoaded();
            }
            sections.clear();
        }
    }

    /**
     * For self-tests: loads the ticking chunk columns covering the block rectangle, then loads (or creates) the section at
     * height {@code y} of each of them, which may not exist yet in empty sky, and holds each loaded (addKeepLoaded) until the
     * returned {@link Held} is released. Completes on the world thread.
     */
    public static CompletableFuture<Held> loadTestArea(World world, int minX, int maxX, int minZ, int maxZ, int y) {
        int cx0 = ChunkUtil.chunkCoordinate(minX);
        int cx1 = ChunkUtil.chunkCoordinate(maxX);
        int cz0 = ChunkUtil.chunkCoordinate(minZ);
        int cz1 = ChunkUtil.chunkCoordinate(maxZ);
        List<CompletableFuture<?>> columns = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                columns.add(loadColumn(world, cx, cz, true).thenAccept(ref -> {
                    if (ref != null && ref.isValid()) {
                        WorldChunk chunk = world.getChunkStore().getStore().getComponent(ref, WorldChunk.getComponentType());
                        if (chunk != null) {
                            chunk.resetKeepAlive();
                        }
                    }
                }));
            }
        }
        return CompletableFuture.allOf(columns.toArray(new CompletableFuture[0])).thenComposeAsync(v -> {
            Held held = new Held();
            List<CompletableFuture<?>> loads = new ArrayList<>();
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    held.wanted++;
                    loads.add(loadSection(world, cx * ChunkUtil.SIZE, y, cz * ChunkUtil.SIZE).thenAccept(ref -> {
                        if (ref != null && ref.isValid()) {
                            ChunkSection section = world.getChunkStore().getStore().getComponent(ref, ChunkSection.getComponentType());
                            if (section != null) {
                                section.addKeepLoaded();
                                held.sections.add(section);
                            }
                        }
                    }));
                }
            }
            return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).thenApplyAsync(w -> held, world);
        }, world);
    }
}
