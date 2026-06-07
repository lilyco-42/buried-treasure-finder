package com.example.buriedtreasure;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores discovered buried-treasure positions.
 *
 * <p>Populated by {@code WorldChunkMixin} whenever a {@code ChestBlockEntity}
 * is added to a client-side chunk at chunk-relative position {@code (9, 9)}
 * inside a beach or snowy-beach biome.
 *
 * <p>Thread-safe for concurrent reads (HUD / render thread) and writes
 * (network thread that processes chunk data).
 */
public final class TreasureData {

    private TreasureData() { /* static utility */ }

    private static final Set<BlockPos> TREASURES =
            Collections.synchronizedSet(new HashSet<>());

    // ---------- write ----------

    /** Records a discovered treasure chest position. */
    public static void addTreasure(BlockPos pos) {
        TREASURES.add(pos.toImmutable());
    }

    /** Removes all recorded treasures (e.g. on world unload). */
    public static void clear() {
        TREASURES.clear();
    }

    // ---------- read ----------

    /** Number of distinct treasure positions currently known. */
    public static int count() {
        return TREASURES.size();
    }

    /**
     * Returns the nearest treasure position to the player, or {@code null}
     * if no treasures are known or the player is out of range (≥ 512 blocks).
     */
    public static BlockPos getNearest() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || TREASURES.isEmpty()) return null;

        BlockPos playerPos = client.player.getBlockPos();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;

        // Iterate over a snapshot so the set can be mutated concurrently.
        synchronized (TREASURES) {
            for (BlockPos pos : TREASURES) {
                double d2 = pos.getSquaredDistance(playerPos);
                if (d2 < best) {
                    best = d2;
                    nearest = pos;
                }
            }
        }

        // Only show treasures within reasonable range (512 blocks)
        if (nearest != null && best < 512.0 * 512.0) {
            return nearest;
        }
        return null;
    }
}
