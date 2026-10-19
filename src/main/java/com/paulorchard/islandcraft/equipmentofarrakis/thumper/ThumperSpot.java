package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * Where a Thumper is planted: the block next to the player in the direction they look (one of the eight around them), the ground in
 * that column, the room above it, and the sand rule exactly as Worms of Arrakis applies it. No engine class.
 */
final class ThumperSpot {

    /** The ground is looked for from this many blocks above the player's feet level down to this many below it. */
    static final int ABOVE = 1;
    static final int BELOW = 2;
    /** Blocks above the ground that must be free of solid blocks (the model is two blocks high). */
    static final int HEADROOM = 2;
    /** Another live Thumper this close (blocks) makes the spot taken. */
    static final double SPOT_RADIUS = 0.8;
    /** Where the Thumper stands, from the centre of the block column, and the horizontal length below which there is no look direction. */
    static final double NO_DIRECTION = 1e-6;

    private ThumperSpot() {
    }

    /**
     * The neighbour cell in the direction of the horizontal look vector {@code (dirX, dirZ)}, rounded to the nearest of the eight
     * compass directions (sectors of 45 degrees around each of them), as {@code {dx, dz}} with each in -1, 0, 1 and not both 0. Null when
     * there is no horizontal direction. Pitch is not an input: the caller passes only the horizontal part of the look.
     */
    static int[] neighbour(double dirX, double dirZ) {
        if (Math.hypot(dirX, dirZ) < NO_DIRECTION) {
            return null;
        }
        double angle = Math.toDegrees(Math.atan2(dirZ, dirX));
        int sector = (int) Math.floor((angle + 22.5) / 45.0);
        sector = ((sector % 8) + 8) % 8;
        return switch (sector) {
            case 0 -> new int[] {1, 0};
            case 1 -> new int[] {1, 1};
            case 2 -> new int[] {0, 1};
            case 3 -> new int[] {-1, 1};
            case 4 -> new int[] {-1, 0};
            case 5 -> new int[] {-1, -1};
            case 6 -> new int[] {0, -1};
            default -> new int[] {1, -1};
        };
    }

    /**
     * The Y of the ground in a column: the first block that is not air looking down from {@code feetY + ABOVE} to {@code feetY - BELOW}
     * (the same "not air" Worms uses: any block id above 0). {@code idAt} gives the block name at a Y, null or empty for air.
     * Returns {@link Integer#MIN_VALUE} when the column has no ground in that range.
     */
    static int ground(int feetY, IntFunction<String> idAt) {
        for (int y = feetY + ABOVE; y >= feetY - BELOW; y--) {
            String id = idAt.apply(y);
            if (id != null && !id.isEmpty()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** True when the {@link #HEADROOM} blocks above the ground are free of solid blocks. */
    static boolean headroom(int groundY, IntPredicate solidAt) {
        for (int k = 1; k <= HEADROOM; k++) {
            if (solidAt.test(groundY + k)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Worms of Arrakis' test for a totem at height {@code y}, mirrored from its {@code AggroManager.onSand}: start at {@code floor(y - 0.1)},
     * go down {@code ceil(tolerance)} blocks, and the first block that is not air must be in the sand set. {@code idAt} gives the block name
     * at a Y in the column of the position, null or empty for air.
     */
    static boolean wormsOnSand(double y, double tolerance, IntFunction<String> idAt, Set<String> sand) {
        int top = (int) Math.floor(y - 0.1);
        int bottom = top - (int) Math.ceil(tolerance);
        for (int at = top; at >= bottom; at--) {
            String id = idAt.apply(at);
            if (id != null && !id.isEmpty()) {
                return sand.contains(id);
            }
        }
        return false;
    }
}
