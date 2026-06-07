package com.example.buriedtreasure;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that maps treasure-map IDs to their corresponding
 * world positions.
 *
 * <p>Populated by {@code MapStateMixin} whenever a treasure-marker decoration
 * (the red "×") is detected on the client.
 */
public final class TreasureData {

    private TreasureData() { /* static utility */ }

    /** Map ID → treasure world position. */
    private static final Map<Integer, BlockPos> TREASURES = new ConcurrentHashMap<>();

    /** The map ID the player is currently holding, or -1 if none. */
    private static volatile int activeMapId = -1;

    // ---------- write ----------

    /**
     * Records the world position of a buried treasure for the given map ID.
     * Overwrites any previous entry for the same map.
     */
    public static void addTreasure(int mapId, int worldX, int worldZ) {
        TREASURES.put(mapId, new BlockPos(worldX, 0, worldZ));
    }

    /** Sets which map the player is currently holding. */
    public static void setActiveMapId(int mapId) {
        activeMapId = mapId;
    }

    // ---------- read ----------

    /**
     * Returns the world position of the treasure for the currently held map,
     * or {@code null} if the player is not holding a known treasure map.
     */
    public static BlockPos getActiveTreasure() {
        if (activeMapId == -1) return null;
        return TREASURES.get(activeMapId);
    }

    /** Clears all stored treasure data. */
    public static void clear() {
        TREASURES.clear();
        activeMapId = -1;
    }
}
