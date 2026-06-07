package com.example.buriedtreasure.mixin;

import com.example.buriedtreasure.BuriedTreasureFinderMod;
import com.example.buriedtreasure.TreasureData;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts block-entity additions to client-side chunks and detects
 * buried-treasure chests.
 *
 * <h3>Detection logic</h3>
 * <ol>
 *   <li>Is the block entity a {@link ChestBlockEntity}?</li>
 *   <li>Is it at chunk-relative position {@code (9, 9)}?
 *       — Buried treasure <b>always</b> generates there in Java Edition.</li>
 *   <li>Is the biome {@code minecraft:beach} or {@code minecraft:snowy_beach}?
 *       — Those are the only vanilla biomes where buried treasure spawns.</li>
 * </ol>
 *
 * <h3>Why this works</h3>
 * When the server sends chunk data to the client (render distance),
 * {@code WorldChunk.addBlockEntity()} is called for every block entity in
 * that chunk — including newly generated treasure chests.  This mixin
 * catches those calls and records the positions.
 */
@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {

    private static final Identifier BEACH = Identifier.ofVanilla("beach");
    private static final Identifier SNOWY_BEACH = Identifier.ofVanilla("snowy_beach");

    /**
     * Runs after every {@code addBlockEntity} call.
     *
     * @param blockEntity the block entity being added to the chunk
     * @param ci          Mixin callback info
     */
    @Inject(method = "addBlockEntity", at = @At("TAIL"))
    private void onAddBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        // ---- gate 1: only on the physical client ----
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }

        // ---- gate 2: only chest-type block entities ----
        if (!(blockEntity instanceof ChestBlockEntity)) {
            return;
        }

        // ---- gate 3: chunk-relative position must be (9, 9) ----
        BlockPos pos = blockEntity.getPos();
        if ((pos.getX() & 15) != 9 || (pos.getZ() & 15) != 9) {
            return;
        }

        // ---- gate 4: must be in a beach or snowy-beach biome ----
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        RegistryEntry<Biome> biomeEntry = client.world.getBiome(pos);
        if (!biomeEntry.matchesId(BEACH) && !biomeEntry.matchesId(SNOWY_BEACH)) {
            return;
        }

        // ---- record ----
        TreasureData.addTreasure(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        BuriedTreasureFinderMod.LOGGER.info(
                "[BuriedTreasure] Chest detected → ({}, {}, {})  chunk-pos=(9,9)  biome=beach",
                pos.getX(), pos.getY(), pos.getZ());
    }
}
