package com.example.buriedtreasure;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buried Treasure Finder — detects buried-treasure chests in nearby chunks
 * through the F3-debug / pie-chart principle: block-entity counting.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>{@link com.example.buriedtreasure.mixin.WorldChunkMixin} intercepts
 *       {@code ChestBlockEntity} additions to client-side chunks.</li>
 *   <li>Chests at chunk-relative position {@code (9, 9)} in beach biomes are
 *       recorded as buried treasure.</li>
 *   <li>A golden beacon beam is rendered at each discovered treasure.</li>
 *   <li>A HUD overlay shows the nearest treasure's coordinates + distance,
 *       plus a chunk-based scanner summary (beach chunks in render distance,
 *       chests found).</li>
 * </ol>
 *
 * <h3>Treasure generation (Java Edition)</h3>
 * Buried treasure chests generate at chunk-local coordinates {@code (9, 9)},
 * placed on top of the highest stone / sandstone / diorite / granite /
 * andesite block that does not exceed ground level, inside beach and
 * snowy-beach biomes.
 */
public class BuriedTreasureFinderMod implements ClientModInitializer {

    public static final String MOD_ID = "buried-treasure";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Identifier BEACH = Identifier.ofVanilla("beach");
    private static final Identifier SNOWY_BEACH = Identifier.ofVanilla("snowy_beach");

    /** Whether the mod is active (toggled via keybind). */
    private static boolean enabled = true;

    /** Toggle keybinding — default {@code K}. */
    private static KeyBinding toggleKey;

    /** Tick counter for periodic statistics refresh. */
    private static int tickCounter;

    /** Cached count of beach-biome chunks currently in render distance. */
    private static int nearbyBeachChunks;

    // ========================================================================
    //  Fabric entry point
    // ========================================================================

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BuriedTreasure] Initializing...");

        // ---- 1. Register toggle keybinding ----
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.buried-treasure.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.buried-treasure"
        ));

        // ---- 2. Per-tick handler (key toggle + stats refresh) ----
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Key toggle
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("§6[BuriedTreasure] "
                                    + (enabled ? "§a✔ ON" : "§c✘ OFF")),
                            true);
                }
                LOGGER.info("[BuriedTreasure] Toggled → {}", enabled ? "ON" : "OFF");
            }
            if (!enabled) return;

            // Refresh beach-chunk count every 40 ticks (2 seconds)
            if (++tickCounter % 40 == 0) {
                refreshStats(client);
            }
        });

        // ---- 3. Beacon rendering (WorldRenderEvents.LAST) ----
        WorldRenderEvents.LAST.register(context -> {
            if (!enabled) return;
            BlockPos treasure = TreasureData.getNearest();
            if (treasure != null) {
                renderBeacon(context, treasure);
            }
        });

        // ---- 4. HUD overlay ----
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!enabled) return;
            renderHud(drawContext);
        });

        LOGGER.info("[BuriedTreasure] Ready! Press K to toggle.");
    }

    // ========================================================================
    //  Statistics — beach‑chunk counting ("pie chart" / F3 debug concept)
    // ========================================================================

    /**
     * Counts how many beach-biome chunks are loaded within the player's
     * render distance.  Mimics what the F3 pie-chart "blockEntities" or
     * "renderedChunks" section would show — the player can use it together
     * with the entity‑count principle to narrow down treasure location.
     */
    private static void refreshStats(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        int renderDist = client.options.getViewDistance().getValue();
        ChunkPos center = client.player.getChunkPos();
        int count = 0;

        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dz = -renderDist; dz <= renderDist; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                // Probe the biome at the chunk's (0,0) world position;
                // ClientWorld.getBiome() returns the default biome for
                // unloaded positions without forcing a load.
                BlockPos probe = new BlockPos(cx << 4, 64, cz << 4);
                RegistryEntry<Biome> biome = client.world.getBiome(probe);
                if (biome.matchesId(BEACH) || biome.matchesId(SNOWY_BEACH)) {
                    count++;
                }
            }
        }
        nearbyBeachChunks = count;
    }

    // ========================================================================
    //  World rendering — golden beacon beam
    // ========================================================================

    private static void renderBeacon(WorldRenderEvents.Last context, BlockPos pos) {
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();

        matrices.push();

        // Centre of the chest block + surface Y
        MinecraftClient client = MinecraftClient.getInstance();
        int surfaceY = pos.getY();
        if (client.world != null) {
            surfaceY = client.world.getTopY(
                    net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                    pos.getX(), pos.getZ());
        }
        matrices.translate(
                pos.getX() + 0.5 - camPos.x,
                surfaceY - camPos.y,
                pos.getZ() + 0.5 - camPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) { matrices.pop(); return; }
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        float half = 0.6f;
        float beamTop = 40f;
        int r = 255, g = 200, b = 0; // gold

        // Bottom cross (X marks the spot)
        line(vc, matrix, -half, 0, -half,  half, 0,  half,  r, g, b, 220);
        line(vc, matrix, -half, 0,  half,  half, 0, -half,  r, g, b, 220);

        // Bottom square
        line(vc, matrix, -half, 0, -half,  half, 0, -half,  r, g, b, 220);
        line(vc, matrix,  half, 0, -half,  half, 0,  half,  r, g, b, 220);
        line(vc, matrix,  half, 0,  half, -half, 0,  half,  r, g, b, 220);
        line(vc, matrix, -half, 0,  half, -half, 0, -half,  r, g, b, 220);

        // Vertical corner lines (beacon beam, fading upward)
        for (int i = 0; i < 4; i++) {
            float cx = (i % 2 == 0) ? -half : half;
            float cz = (i < 2) ? -half : half;
            line(vc, matrix, cx, 0, cz, cx * 0.5f, beamTop, cz * 0.5f, r, g, b, 80);
        }

        // Top ring
        float bh = half * 0.6f;
        line(vc, matrix, -bh, beamTop, -bh,  bh, beamTop, -bh,  r, g, b, 80);
        line(vc, matrix,  bh, beamTop, -bh,  bh, beamTop,  bh,  r, g, b, 80);
        line(vc, matrix,  bh, beamTop,  bh, -bh, beamTop,  bh,  r, g, b, 80);
        line(vc, matrix, -bh, beamTop,  bh, -bh, beamTop, -bh,  r, g, b, 80);

        matrices.pop();
    }

    private static void line(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             int r, int g, int b, int a) {
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a);
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a);
    }

    // ========================================================================
    //  HUD overlay — debug / pie‑chart style info panel
    // ========================================================================

    private static void renderHud(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        TextRenderer tr = client.textRenderer;
        int x = 10, y = 10;

        // ---- scanner status line ----
        int renderDist = client.options.getViewDistance().getValue();
        int totalChunks = (renderDist * 2 + 1) * (renderDist * 2 + 1);
        String status = "§6◆ Scanner §7| §fRD:" + renderDist
                + " §7| §aBeach:" + nearbyBeachChunks
                + " §7| §eChests:" + TreasureData.count();
        ctx.drawText(tr, Text.literal(status), x, y, 0xFFFFFF, true);
        y += 14;

        // ---- nearest treasure ----
        BlockPos nearest = TreasureData.getNearest();
        if (nearest != null) {
            BlockPos pp = client.player.getBlockPos();
            int dist = (int) Math.sqrt(nearest.getSquaredDistance(pp));
            String info = "§6★ Treasure §f("
                    + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ()
                    + ") §e" + dist + "m";
            ctx.drawText(tr, Text.literal(info), x, y, 0xFFFFFF, true);
        } else if (nearbyBeachChunks > 0) {
            ctx.drawText(tr,
                    Text.literal("§7  → Move around beach chunks to detect..."), x, y, 0xFFFFFF, true);
        } else {
            ctx.drawText(tr,
                    Text.literal("§7  → No beach chunks in range"), x, y, 0xFFFFFF, true);
        }
    }

    // ========================================================================
    //  public API
    // ========================================================================

    public static boolean isEnabled() {
        return enabled;
    }
}
