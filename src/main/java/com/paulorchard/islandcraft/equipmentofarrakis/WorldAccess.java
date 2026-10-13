package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthModule;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Block, block health and chunk loading for a loaded world on Hytale 0.7.0, where chunks are ECS columns plus sections and World
 * no longer has getBlock, setBlock or getChunkAsync. Create one per operation (it caches sections while it lives, so never keep
 * it) and use it on the world thread.
 */
final class WorldAccess {

    private final ChunkStore chunks;
    private final SectionReader sections;

    WorldAccess(World world) {
        this.chunks = world.getChunkStore();
        this.sections = new SectionReader(chunks);
    }

    /** The section containing this block, or null when it is not loaded. */
    Ref<ChunkStore> sectionRef(int x, int y, int z) {
        Ref<ChunkStore> ref = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        return ref != null && ref.isValid() ? ref : null;
    }

    /** Block id at a world position; 0 (air) when the section is not loaded. */
    int block(int x, int y, int z) {
        return sections.getBlock(x, y, z);
    }

    /** Null for air, an unknown id, or a section that is not loaded. */
    BlockType blockType(int x, int y, int z) {
        return blockTypeOf(block(x, y, z));
    }

    static BlockType blockTypeOf(int blockId) {
        return blockId <= 0 ? null : BlockType.getAssetMap().getAsset(blockId);
    }

    /**
     * Sets the block at a world position, rotation 0 and filler 0; false when its section is not loaded or the name is unknown.
     * {@code settings} are SetBlockSettings flags (0 = the engine's normal behaviour). "Empty" removes the block.
     */
    boolean setBlock(int x, int y, int z, String blockName, int settings) {
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
    BlockHealthSection healthSection(int x, int y, int z) {
        Ref<ChunkStore> section = sectionRef(x, y, z);
        return section == null ? null
                : chunks.getStore().getComponent(section, BlockHealthModule.get().getBlockHealthSectionComponentType());
    }

    /** The ChunkSection (save mark, keep-loaded counter) containing this position, or null when it is not loaded. */
    ChunkSection chunkSection(int x, int y, int z) {
        Ref<ChunkStore> section = sectionRef(x, y, z);
        return section == null ? null : chunks.getStore().getComponent(section, ChunkSection.getComponentType());
    }

    /**
     * Loads a chunk column, ticking or not. The raw ChunkStore future does not complete on the world thread, so this hops back
     * with thenApplyAsync(..., world). The ref is null if the chunk could not be loaded; callers still check isValid().
     */
    static CompletableFuture<Ref<ChunkStore>> loadColumn(World world, int chunkX, int chunkZ, boolean ticking) {
        return world.getChunkStore()
                .getChunkReferenceAsync(ChunkUtil.indexChunk(chunkX, chunkZ), ticking ? GetChunkFlags.SET_TICKING : 0)
                .thenApplyAsync(ref -> ref, world);
    }

    /** Loads (or creates) the section containing a world position; completes on the world thread with its ref. */
    static CompletableFuture<Ref<ChunkStore>> loadSection(World world, int x, int y, int z) {
        return world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x, y, z).thenApplyAsync(ref -> ref, world);
    }

    /** Sections a development test holds loaded; {@link #release} lets them go. World thread only. */
    static final class Held {
        private final List<ChunkSection> sections = new ArrayList<>();
        /** How many sections were asked for, and how many of them are now loaded and held. */
        int wanted;

        int loaded() {
            return sections.size();
        }

        void release() {
            for (ChunkSection section : sections) {
                section.removeKeepLoaded();
            }
            sections.clear();
        }
    }

    /**
     * For the self-tests: loads the ticking chunk columns covering the block rectangle, then loads (or creates) the section at
     * height {@code y} of each of them, which may not exist yet in empty sky, and holds each loaded (addKeepLoaded) until the
     * returned {@link Held} is released. Completes on the world thread.
     */
    static CompletableFuture<Held> loadTestArea(World world, int minX, int maxX, int minZ, int maxZ, int y) {
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
