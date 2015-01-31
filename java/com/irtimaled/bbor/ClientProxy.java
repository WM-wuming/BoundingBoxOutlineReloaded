package com.irtimaled.bbor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.glu.Sphere;

import java.awt.*;
import java.util.Map;
import java.util.Set;

public class ClientProxy extends CommonProxy {

    @SubscribeEvent
    public void onKeyInputEvent(InputEvent.KeyInputEvent evt) {
        if (hotKey.isPressed()) {
            active = !active;
        }
    }

    private boolean active;
    private KeyBinding hotKey;

    @Override
    public void init() {
        super.init();
        hotKey = new KeyBinding("key.bbor.hotKey", Keyboard.KEY_B, "key.categories.bbor");
        ClientRegistry.registerKeyBinding(hotKey);
    }

    private double playerX;
    private double playerY;
    private double playerZ;

    @SubscribeEvent
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        EntityPlayer entityPlayer = Minecraft.getMinecraft().thePlayer;
        playerX = entityPlayer.lastTickPosX + (entityPlayer.posX - entityPlayer.lastTickPosX) * (double) event.partialTicks;
        playerY = entityPlayer.lastTickPosY + (entityPlayer.posY - entityPlayer.lastTickPosY) * (double) event.partialTicks;
        playerZ = entityPlayer.lastTickPosZ + (entityPlayer.posZ - entityPlayer.lastTickPosZ) * (double) event.partialTicks;

        if (this.active) {
            int activeDimensionId = entityPlayer.worldObj.provider.getDimensionId();
            if (boundingBoxCacheMap.containsKey(activeDimensionId)) {
                renderBoundingBoxes(boundingBoxCacheMap.get(activeDimensionId).getBoundingBoxes());
            }
        }
    }

    private void renderBoundingBoxes(Map<BoundingBox, Set<BoundingBox>> map) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(3.0f);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);

        if (configManager.alwaysVisible.getBoolean()) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }

        for (BoundingBox bb : map.keySet()) {
            renderBoundingBoxes(map.get(bb));
        }

        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);

        if (configManager.showDebugInfo.getBoolean()) {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution var5 = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int screenWidth = var5.getScaledWidth();
            mc.entityRenderer.setupOverlayRendering();
            int count = 0;
            for (BoundingBox bb : map.keySet()) {
                count += map.get(bb).size();
            }
            String debug = String.format("%d/%d", map.keySet().size(), count);
            int width = screenWidth - mc.fontRendererObj.getStringWidth(debug);

            mc.fontRendererObj.drawStringWithShadow(debug, width - 2, 2, 16777215);
        }
    }

    private void renderBoundingBoxes(Set<BoundingBox> bbList) {
        World world = Minecraft.getMinecraft().theWorld;
        Set activeChunks = ReflectionHelper.getPrivateValue(World.class, world, 33);
        for (BoundingBox bb : bbList) {

            if (activeChunks.contains(world.getChunkFromBlockCoords(bb.getMinBlockPos()).getChunkCoordIntPair()) ||
                    activeChunks.contains(world.getChunkFromBlockCoords(bb.getMaxBlockPos()).getChunkCoordIntPair())) {

                if (bb instanceof BoundingBoxVillage) {
                    BoundingBoxVillage villageBB = (BoundingBoxVillage) bb;
                    if (configManager.renderVillageAsSphere.getBoolean()) {
                        renderBoundingBoxVillageAsSphere(villageBB);
                    } else {
                        renderBoundingBox(villageBB);
                    }
                    if (configManager.drawIronGolemSpawnArea.getBoolean() &&
                            villageBB.getSpawnsIronGolems()) {
                        renderIronGolemSpawnArea(villageBB);
                    }
                } else if (bb instanceof BoundingBoxSlimeChunk) {
                    renderSlimeChunk((BoundingBoxSlimeChunk) bb);
                } else {
                    renderBoundingBox(bb);
                }
            }
        }
    }

    private void renderBoundingBox(BoundingBox bb) {
        AxisAlignedBB aaBB = bb.toAxisAlignedBB();
        Color color = bb.getColor();
        renderCuboid(aaBB.addCoord(0, 1, 0), color, fill());
    }

    private void renderSlimeChunk(BoundingBoxSlimeChunk bb) {
        AxisAlignedBB aaBB = bb.toAxisAlignedBB();
        Color color = bb.getColor();
        renderCuboid(aaBB.addCoord(0, 1, 0), color, fill());

        double maxY = configManager.slimeChunkMaxY.getInt();
        if ((maxY == 0) || (playerY < maxY)) {
            maxY = playerY;
        }

        if (maxY > 39) {
            aaBB = new AxisAlignedBB(aaBB.minX, 39, aaBB.minZ, aaBB.maxX, maxY, aaBB.maxZ);
            renderCuboid(aaBB, color, fill());
        }
    }

    private boolean fill() {
        return configManager.fill.getBoolean();
    }

    private void renderIronGolemSpawnArea(BoundingBoxVillage villageBB) {
        BlockPos center = villageBB.getCenter();
        AxisAlignedBB abb = new AxisAlignedBB(new BlockPos(center.getX() - 8,
                center.getY() - 3,
                center.getZ() - 8),
                new BlockPos(center.getX() + 8,
                        center.getY() + 4,
                        center.getZ() + 8));
        GL11.glLineWidth(2.0f);
        renderCuboid(abb, villageBB.getColor(), false);
        GL11.glLineWidth(3.0f);
    }

    private void renderCuboid(AxisAlignedBB aaBB, Color color, boolean fill) {
        aaBB = offsetAxisAlignedBB(aaBB);
        if (fill) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            renderCuboid(aaBB, 30, color);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE);
            GL11.glPolygonOffset(-1.f, -1.f);
        }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        renderCuboid(aaBB, 255, color);
    }

    private void renderCuboid(AxisAlignedBB bb, int alphaChannel, Color color) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int colorR = color.getRed();
        int colorG = color.getGreen();
        int colorB = color.getBlue();

        worldRenderer.startDrawing(GL11.GL_QUADS);
        worldRenderer.setColorRGBA(colorR, colorG, colorB, alphaChannel);
        worldRenderer.addVertex(bb.minX, bb.minY, bb.minZ);
        worldRenderer.addVertex(bb.maxX, bb.minY, bb.minZ);
        worldRenderer.addVertex(bb.maxX, bb.minY, bb.maxZ);
        worldRenderer.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.draw();

        if (bb.minY == bb.maxY) {
            return;
        }

        worldRenderer.startDrawing(GL11.GL_QUADS);
        worldRenderer.setColorRGBA(colorR, colorG, colorB, alphaChannel);
        worldRenderer.addVertex(bb.minX, bb.maxY, bb.minZ);
        worldRenderer.addVertex(bb.maxX, bb.maxY, bb.minZ);
        worldRenderer.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        worldRenderer.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.draw();

        worldRenderer.startDrawing(GL11.GL_QUADS);
        worldRenderer.setColorRGBA(colorR, colorG, colorB, alphaChannel);
        worldRenderer.addVertex(bb.minX, bb.minY, bb.maxZ);
        worldRenderer.addVertex(bb.minX, bb.maxY, bb.maxZ);
        worldRenderer.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        worldRenderer.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.draw();

        worldRenderer.startDrawing(GL11.GL_QUADS);
        worldRenderer.setColorRGBA(colorR, colorG, colorB, alphaChannel);
        worldRenderer.addVertex(bb.minX, bb.minY, bb.minZ);
        worldRenderer.addVertex(bb.minX, bb.maxY, bb.minZ);
        worldRenderer.addVertex(bb.maxX, bb.maxY, bb.minZ);
        worldRenderer.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.draw();

        worldRenderer.startDrawing(GL11.GL_QUADS);
        worldRenderer.setColorRGBA(colorR, colorG, colorB, alphaChannel);
        worldRenderer.addVertex(bb.minX, bb.minY, bb.minZ);
        worldRenderer.addVertex(bb.minX, bb.minY, bb.maxZ);
        worldRenderer.addVertex(bb.minX, bb.maxY, bb.maxZ);
        worldRenderer.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.draw();

        worldRenderer.startDrawing(GL11.GL_QUADS);
        worldRenderer.setColorRGBA(colorR, colorG, colorB, alphaChannel);
        worldRenderer.addVertex(bb.maxX, bb.minY, bb.minZ);
        worldRenderer.addVertex(bb.maxX, bb.minY, bb.maxZ);
        worldRenderer.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        worldRenderer.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.draw();
    }

    private AxisAlignedBB offsetAxisAlignedBB(AxisAlignedBB axisAlignedBB) {
        double expandBy = 0.005F;
        return axisAlignedBB
                .addCoord(1, 0, 1)
                .expand(expandBy, expandBy, expandBy)
                .offset(-playerX, -playerY, -playerZ);
    }

    private void renderBoundingBoxVillageAsSphere(BoundingBoxVillage bb) {
        BlockPos center = bb.getCenter();
        int radius = bb.getRadius();
        Color color = bb.getColor();
        renderSphere(center, radius, color);
    }

    private void renderSphere(BlockPos center, int radius, Color color) {
        int colorR = color.getRed();
        int colorG = color.getGreen();
        int colorB = color.getBlue();

        double x = center.getX() - playerX;
        double y = center.getY() - playerY;
        double z = center.getZ() - playerZ;
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_CONSTANT_COLOR);
        GL11.glPointSize(2.5f);
        GL11.glTranslated(x, y, z);
        Sphere sphere = new Sphere();
        sphere.setDrawStyle(GLU.GLU_POINT);
        sphere.setNormals(GLU.GLU_FLAT);
        sphere.setOrientation(GLU.GLU_OUTSIDE);
        GL11.glColor3f(colorR / 255.0f, colorG / 255.0f, colorB / 255.0f);
        GL11.glRotatef(90f, 0, 0, 0);
        sphere.draw(radius, 48, 48);
        GL11.glRotatef(-90f, 0, 0, 0);
        GL11.glTranslated(-x, -y, -z);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
    }
}