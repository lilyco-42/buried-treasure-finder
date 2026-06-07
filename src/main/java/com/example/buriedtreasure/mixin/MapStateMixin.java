package com.example.buriedtreasure.mixin;

import com.example.buriedtreasure.BuriedTreasureFinderMod;
import com.example.buriedtreasure.TreasureData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that observes decoration additions on {@link MapState}.
 *
 * <p>When the server sends map data to the client, the client-side
 * {@code MapState} receives decoration entries — including the red "×"
 * that marks a buried treasure on explorer maps.
 *
 * <p>This mixin intercepts every {@code addDecoration} call, checks whether
 * the decoration type is {@code minecraft:target_x} (the vanilla buried-
 * treasure marker), and if so records the world-space coordinates.
 *
 * <h3>Coordinate calculation</h3>
 * <pre>
 *   worldX = mapState.centerX + decoration.x (signed byte)
 *   worldZ = mapState.centerZ + decoration.z (signed byte)
 * </pre>
 *
 * <h3>Side safety</h3>
 * The mixin bails out early when not running on the physical client
 * ({@link FabricLoader#getEnvironmentType()}).  This avoids recording
 * treasure positions on a dedicated server where the rendering overlay
 * would be meaningless.
 */
@Mixin(MapState.class)
public abstract class MapStateMixin {

    /**
     * The vanilla identifier for the red-X decoration used by explorer maps
     * that point to buried treasure.
     */
    private static final Identifier RED_X = Identifier.ofVanilla("target_x");

    /**
     * Injects at the tail of {@code MapState.addDecoration} so that the
     * decoration has already been stored in the map's internal collection.
     *
     * @param type     the type of the decoration being added
     * @param x        signed byte — X offset from the map centre
     * @param z        signed byte — Z offset from the map centre
     * @param rotation rotation of the icon (0–15)
     * @param text     optional label (always null for treasure markers)
     * @param ci       Mixin callback info
     */
    @Inject(method = "addDecoration", at = @At("TAIL"))
    private void onAddDecoration(RegistryEntry<MapDecorationType> type,
                                 byte x, byte z,
                                 byte rotation,
                                 Text text,
                                 CallbackInfo ci) {
        // Only run on the physical client — the server has nothing to render.
        if (FabricLoader.getInstance().getEnvironmentType()
                != net.fabricmc.api.EnvType.CLIENT) {
            return;
        }

        // Guard: is this a buried-treasure marker?
        // The vanilla explorer-map loot function uses "minecraft:target_x".
        if (!type.matchesId(RED_X)) {
            return;
        }

        // Cast 'this' to access the public fields centreX / centreZ.
        MapState self = (MapState) (Object) this;
        int worldX = self.centerX + x;
        int worldZ = self.centerZ + z;

        TreasureData.addTreasure(self.getId(), worldX, worldZ);

        BuriedTreasureFinderMod.LOGGER.info(
                "[BuriedTreasure] Treasure marker captured → world ({}, {}), map id {}",
                worldX, worldZ, self.getId());
    }
}
