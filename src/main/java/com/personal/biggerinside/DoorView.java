package com.personal.biggerinside;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws a huge hall in the doorway of every open TARDIS exterior near the player.
 * The picture is a flat quad in the doorway, but it is ray traced from the viewer's real position
 * ({@link InteriorScene}), so walking around makes the hall shift like a real space behind the door.
 */
@Mod.EventBusSubscriber(modid = BiggerInsideMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DoorView {
    private static final int MAX_TEX = 4;
    private static final DynamicTexture[] TEX = new DynamicTexture[MAX_TEX];
    private static final ResourceLocation[] TEX_ID = new ResourceLocation[MAX_TEX];
    private static final long[] LAST_UPDATE = new long[MAX_TEX];
    private static final int[] PIXELS = new int[InteriorScene.W * InteriorScene.H];

    private static final List<BlockEntity> EXTERIORS = new ArrayList<>();
    private static long lastScan = Long.MIN_VALUE;
    private static boolean botiHandled;
    private static int botiTries;
    private static boolean loggedFirst;
    private static boolean loggedOpenError;
    private static boolean loggedBehind;
    private static String doorDebug = "";
    private static long lastOverlay = Long.MIN_VALUE;
    private static String lastSummary = "";

    private DoorView() {
    }

    private record Candidate(BlockEntity be, double dist) {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (!ViewConfig.ENABLED.get()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        try {
            long now = level.getGameTime();
            if (lastScan == Long.MIN_VALUE || Math.abs(now - lastScan) >= 20) {
                lastScan = now;
                scan(level, mc.player.blockPosition());
            }
            if (EXTERIORS.isEmpty()) {
                summary(0, 0, 0, 0);
                return;
            }

            Vec3 cam = event.getCamera().getPosition();
            double range = ViewConfig.RANGE.get();
            List<Candidate> open = new ArrayList<>();
            for (BlockEntity be : EXTERIORS) {
                if (be.isRemoved()) continue;
                BlockPos bp = be.getBlockPos();
                double d = Math.sqrt(bp.distToCenterSqr(cam.x, cam.y, cam.z));
                if (d > range) continue;
                if (!isDoorOpen(be)) continue;
                open.add(new Candidate(be, d));
            }
            if (open.isEmpty()) {
                summary(EXTERIORS.size(), 0, 0, 0);
                return;
            }
            open.sort(Comparator.comparingDouble(Candidate::dist));

            double time = ViewConfig.ANIMATE.get() ? (now + event.getPartialTick()) / 20.0 : 0.0;
            int count = Math.min(open.size(), Math.min(MAX_TEX, ViewConfig.MAX_DOORS.get()));
            boolean stateSet = false;
            int drawn = 0;
            for (int i = 0; i < count; i++) {
                if (drawDoor(i, open.get(i).be(), event, cam, time, !stateSet)) {
                    stateSet = true;
                    drawn++;
                }
            }
            summary(EXTERIORS.size(), open.size(), drawn, 0);
            if (stateSet) {
                RenderSystem.polygonOffset(0.0f, 0.0f);
                RenderSystem.disablePolygonOffset();
                RenderSystem.enableCull();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        } catch (Throwable t) {
            if (!loggedFirst) {
                loggedFirst = true;
                BiggerInsideMod.LOGGER.warn("Doorway view skipped a frame: {}", t.toString());
            }
        }
    }

    private static void summary(int found, int open, int drawn, int unused) {
        String s = "exteriors=" + found + " openDoors=" + open + " drawn=" + drawn;
        if (ViewConfig.DEBUG_OVERLAY.get() && found > 0) {
            Minecraft mc = Minecraft.getInstance();
            long t = mc.level == null ? 0 : mc.level.getGameTime();
            if (mc.player != null && (lastOverlay == Long.MIN_VALUE || Math.abs(t - lastOverlay) >= 10)) {
                lastOverlay = t;
                mc.player.displayClientMessage(Component.literal("BiggerInside: " + s + " " + doorDebug), true);
            }
        }
        if (!s.equals(lastSummary)) {
            lastSummary = s;
            BiggerInsideMod.LOGGER.info("Doorway view: {}", s);
        }
    }

    private static void scan(ClientLevel level, BlockPos around) {
        EXTERIORS.clear();
        int cx = around.getX() >> 4;
        int cz = around.getZ() >> 4;
        int radius = Math.max(1, (ViewConfig.RANGE.get() >> 4) + 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                List<BlockEntity> bes = new ArrayList<>(chunk.getBlockEntities().values());
                for (BlockEntity be : bes) {
                    if (be.getClass().getName().endsWith("ExteriorBlockEntity")) {
                        AitBridge.rememberLoader(be);
                        EXTERIORS.add(be);
                    }
                }
            }
        }
    }

    private static boolean isDoorOpen(BlockEntity be) {
        try {
            Object ref = AitBridge.call(be, "tardis");
            Object tardis = AitBridge.call(ref, "get");
            if (tardis == null) return false;
            Object door = AitBridge.call(tardis, "door");
            if (!(AitBridge.call(door, "isOpen") instanceof Boolean open) || !open) return false;
            if (ViewConfig.REQUIRE_LANDED.get()) {
                try {
                    Object travel = AitBridge.call(tardis, "travel");
                    Object state = AitBridge.call(travel, "getState");
                    String name = String.valueOf(state);
                    if (name.contains("DEMAT") || name.contains("FLIGHT") || name.contains("REMAT")
                            || name.contains("MAT") && !name.contains("LANDED")) {
                        return false;
                    }
                } catch (Throwable ignored) {
                    // cannot tell the travel state: just show the view
                }
            }
            return true;
        } catch (Throwable t) {
            if (!loggedOpenError) {
                loggedOpenError = true;
                BiggerInsideMod.LOGGER.warn("Could not read a TARDIS door state: {}", t.toString());
            }
            return false;
        }
    }

    private static int rotationOf(BlockEntity be) {
        BlockState state = be.getBlockState();
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntegerProperty ip && p.getName().equals("rotation")) {
                return state.getValue(ip);
            }
        }
        return 0;
    }

    private static boolean drawDoor(int slot, BlockEntity be, RenderLevelStageEvent event, Vec3 cam, double time, boolean setState) {
        double yaw = Math.toRadians(rotationOf(be) * 22.5 + ViewConfig.FACING_OFFSET.get());
        double fx = Math.sin(yaw), fz = Math.cos(yaw);    // outward direction of the door
        double rx = -fz, rz = fx;                         // viewer's right when looking at the door

        BlockPos bp = be.getBlockPos();
        double off = ViewConfig.PLANE_OFFSET.get();
        double cxw = bp.getX() + 0.5 + fx * off;
        double cyw = bp.getY() + ViewConfig.DOOR_BOTTOM.get();
        double czw = bp.getZ() + 0.5 + fz * off;

        double relx = cam.x - cxw, rely = cam.y - cyw, relz = cam.z - czw;
        double ez = relx * fx + relz * fz;
        if (ez < 0.12) {                                  // behind the doorway: nothing to show
            if (!loggedBehind) {
                loggedBehind = true;
                BiggerInsideMod.LOGGER.info("Doorway view: you are on the back side of this TARDIS's door as the add-on sees it. "
                        + "If you are standing in front of the door, set facingOffsetDegrees to 180 in biggerinside-client.toml.");
            }
            return false;
        }
        double ex = relx * rx + relz * rz;
        doorDebug = String.format("rot=%d ez=%.1f ex=%.1f", rotationOf(be), ez, ex);

        double hw = ViewConfig.DOOR_WIDTH.get() / 2.0;
        double dh = ViewConfig.DOOR_HEIGHT.get();

        ensureTexture(slot);
        long nanos = System.nanoTime();
        if (nanos - LAST_UPDATE[slot] > 33_000_000L) {
            LAST_UPDATE[slot] = nanos;
            InteriorScene.render(PIXELS, ex, rely, ez, hw, dh, time);
            NativeImage img = TEX[slot].getPixels();
            if (img != null) {
                int w = InteriorScene.W, h = InteriorScene.H;
                float k = ViewConfig.BRIGHTNESS.get().floatValue();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int p = PIXELS[y * w + x];
                        if (k != 1.0f) p = scale(p, k);
                        img.setPixelRGBA(x, y, p);
                    }
                }
                TEX[slot].upload();
            }
        }

        if (!botiHandled) handleBoti();

        // corners, relative to the camera
        double x0 = cxw - rx * hw - cam.x, z0 = czw - rz * hw - cam.z;
        double x1 = cxw + rx * hw - cam.x, z1 = czw + rz * hw - cam.z;
        double yb = cyw - cam.y, yt = yb + dh;

        if (setState) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-3.0f, -3.0f);
        }
        RenderSystem.setShaderTexture(0, TEX_ID[slot]);

        Matrix4f m = event.getPoseStack().last().pose();
        float eu = 0.5f / InteriorScene.W, ev = 0.5f / InteriorScene.H;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.vertex(m, (float) x0, (float) yb, (float) z0).uv(eu, 1f - ev).color(255, 255, 255, 255).endVertex();
        bb.vertex(m, (float) x1, (float) yb, (float) z1).uv(1f - eu, 1f - ev).color(255, 255, 255, 255).endVertex();
        bb.vertex(m, (float) x1, (float) yt, (float) z1).uv(1f - eu, ev).color(255, 255, 255, 255).endVertex();
        bb.vertex(m, (float) x0, (float) yt, (float) z0).uv(eu, ev).color(255, 255, 255, 255).endVertex();
        BufferUploader.drawWithShader(bb.end());
        return true;
    }

    private static int scale(int abgr, float k) {
        int r = Math.min(255, (int) ((abgr & 255) * k));
        int g = Math.min(255, (int) (((abgr >> 8) & 255) * k));
        int b = Math.min(255, (int) (((abgr >> 16) & 255) * k));
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static void ensureTexture(int slot) {
        if (TEX[slot] != null) return;
        DynamicTexture t = new DynamicTexture(InteriorScene.W, InteriorScene.H, false);
        ResourceLocation id = new ResourceLocation(BiggerInsideMod.MOD_ID, "doorway_view_" + slot);
        Minecraft.getInstance().getTextureManager().register(id, t);
        t.setFilter(true, false);
        TEX[slot] = t;
        TEX_ID[slot] = id;
    }

    /** Turns off AiT's own doorway view (in memory only) so it does not draw under ours. */
    private static void handleBoti() {
        if (!ViewConfig.DISABLE_AIT_BOTI.get()) {
            botiHandled = true;
            return;
        }
        if (++botiTries > 5) {
            botiHandled = true;
            return;
        }
        try {
            Class<?> client = AitBridge.findClass("dev.amble.ait.client.AITModClient");
            Object cfg = client.getField("CONFIG").get(null);
            if (cfg != null) {
                cfg.getClass().getField("enableTardisBOTI").setBoolean(cfg, false);
                botiHandled = true;
            }
        } catch (Throwable t) {
            BiggerInsideMod.LOGGER.debug("Could not switch off AiT's doorway view: {}", t.toString());
        }
    }
}
