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
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buried Treasure Finder — automatically calculates and highlights buried
 * treasure locations when holding a treasure map.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>{@link com.example.buriedtreasure.mixin.MapStateMixin} captures the
 *       world coordinates of the red "×" marker when the map decoration is
 *       added on the client.</li>
 *   <li>This class tracks which map the player is currently holding.</li>
 *   <li>Each frame a golden beacon beam and HUD overlay are rendered at the
 *       treasure position.</li>
 * </ol>
 *
 * <h3>Keybindings</h3>
 * Press {@code K} to toggle the feature on/off (configurable in Controls).
 *
 * <h3>Treasure generation (Java Edition)</h3>
 * Buried treasure generates at chunk-local coordinates (9, 9) on the highest
 * stone / sandstone / diorite / granite / andesite block that does not exceed
 * ground level. The chest replaces whatever block is at that position.
 */
public class BuriedTreasureFinderMod implements ClientModInitializer {

    public static final String MOD_ID = "buried-treasure";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Whether the feature is currently active. */
    private static boolean enabled = true;

    /** Toggle keybinding (default: K). */
    private static KeyBinding toggleKey;

    // ---------- Fabric entry point ----------

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BuriedTreasure] Initializing...");

        // 1. Register toggle keybinding
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.buried-treasure.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.buried-treasure"
        ));

        // 2. Detect key presses each tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("§6[BuriedTreasure] " + (enabled ? "§a✔ ON" : "§c✘ OFF")),
                            true // action bar
                    );
                }
                LOGGER.info("[BuriedTreasure] Toggled → {}", enabled ? "ON" : "OFF");
            }

            // Track which map the player is holding
            if (enabled && client.player != null) {
                int mapId = getHeldMapId(client.player.getMainHandStack());
                if (mapId == -1) {
                    mapId = getHeldMapId(client.player.getOffHandStack());
                }
                TreasureData.setActiveMapId(mapId);
            } else {
                TreasureData.setActiveMapId(-1);
            }
        });

        // 3. Render golden beacon beam in the world
        WorldRenderEvents.LAST.register(context -> {
            if (!enabled) return;
            BlockPos treasurePos = TreasureData.getActiveTreasure();
            if (treasurePos == null) return;
            renderBeacon(context, treasurePos);
        });

        // 4. Render HUD overlay with coordinates & distance
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!enabled) return;
            BlockPos treasurePos = TreasureData.getActiveTreasure();
            MinecraftClient client = MinecraftClient.getInstance();
            if (treasurePos == null || client.player == null) return;
            renderHudOverlay(drawContext, client, treasurePos);
        });

        LOGGER.info("[BuriedTreasure] Ready! Press K to toggle.");
    }

    // ---------- public API ----------

    public static boolean isEnabled() {
        return enabled;
    }

    // ---------- helper: extract map id from item stack ----------

    /**
     * Returns the map ID stored in the given stack, or -1 if the stack is not
     * a filled map or has no map-id component.
     */
    private static int getHeldMapId(ItemStack stack) {
        if (!stack.isOf(Items.FILLED_MAP)) return -1;
        // In 1.21.x the map id lives in DataComponentTypes.MAP_ID
        var comp = stack.get(net.minecraft.component.DataComponentTypes.MAP_ID);
        return comp != null ? comp.id() : -1;
    }

    // ---------- world rendering ----------

    /**
     * Renders a golden beacon-beam outline at {@code pos} so the player can
     * see the exact treasure location from a distance.
     */
    private static void renderBeacon(WorldRenderEvents.Last context, BlockPos pos) {
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();

        matrices.push();

        // Translate to the treasure block centre (X+0.5, Z+0.5) at surface level
        MinecraftClient client = MinecraftClient.getInstance();
        int surfaceY = pos.getY();
        if (client.world != null) {
            surfaceY = client.world.getTopY(
                    net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                    pos.getX(),
                    pos.getZ()
            );
        }

        double dx = pos.getX() + 0.5 - camPos.x;
        double dy = surfaceY - camPos.y;
        double dz = pos.getZ() + 0.5 - camPos.z;
        matrices.translate(dx, dy, dz);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            matrices.pop();
            return;
        }
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());

        float half = 0.6f;
        float beamTop = 40f;
        // Golden colour with alpha fade toward the top
        int r = 255, g = 200, b = 0;

        // --- Bottom cross (X marks the spot) ---
        line(consumer, matrix, -half, 0, -half, half, 0, half, r, g, b, 220);
        line(consumer, matrix, -half, 0, half, half, 0, -half, r, g, b, 220);

        // --- Bottom square ---
        line(consumer, matrix, -half, 0, -half, half, 0, -half, r, g, b, 220);
        line(consumer, matrix, half, 0, -half, half, 0, half, r, g, b, 220);
        line(consumer, matrix, half, 0, half, -half, 0, half, r, g, b, 220);
        line(consumer, matrix, -half, 0, half, -half, 0, -half, r, g, b, 220);

        // --- Vertical corner lines (beacon beam) ---
        float bh = half * 0.6f;
        for (int i = 0; i < 4; i++) {
            float cx = (i % 2 == 0) ? -half : half;
            float cz = (i < 2) ? -half : half;
            line(consumer, matrix, cx, 0, cz, cx * 0.5f, beamTop, cz * 0.5f, r, g, b, 80);
        }

        // --- Top ring ---
        line(consumer, matrix, -bh, beamTop, -bh, bh, beamTop, -bh, r, g, b, 80);
        line(consumer, matrix, bh, beamTop, -bh, bh, beamTop, bh, r, g, b, 80);
        line(consumer, matrix, bh, beamTop, bh, -bh, beamTop, bh, r, g, b, 80);
        line(consumer, matrix, -bh, beamTop, bh, -bh, beamTop, -bh, r, g, b, 80);

        matrices.pop();
    }

    /** Draws a single coloured line segment. */
    private static void line(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             int r, int g, int b, int a) {
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a);
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a);
    }

    // ---------- HUD overlay ----------

    private static void renderHudOverlay(DrawContext drawContext, MinecraftClient client, BlockPos treasurePos) {
        TextRenderer textRenderer = client.textRenderer;
        BlockPos playerPos = client.player.getBlockPos();
        int distance = (int) Math.sqrt(playerPos.getSquaredDistance(treasurePos));

        String line1 = "§6★ §fTreasure: §e" + treasurePos.getX() + ", " + treasurePos.getZ();
        String line2 = "§7Distance: §a" + distance + "m";

        int y = 10;
        drawContext.drawText(textRenderer, Text.literal(line1), 10, y, 0xFFFFFF, true);
        drawContext.drawText(textRenderer, Text.literal(line2), 10, y + 12, 0xFFFFFF, true);
    }
}
