package cn.furina.ae2split.client;

import appeng.blockentity.crafting.CraftingBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 分裂元件方块表面闪烁点的客户端渲染器（MC 1.20.1 / Forge）。
 *
 * <p>只有方块属于一个已经成型的 CPU 多方块时才渲染：成型且已供能 = 绿色快速闪烁，
 * 成型但未供能 = 琥珀色缓慢闪烁，未成型 = 什么都不画。成型/供能状态全部来自
 * {@link CraftingBlockEntity#isFormed()} / {@link CraftingBlockEntity#isPowered()}；
 * 在客户端这两个方法读的是同步过来的方块状态（FORMED / POWERED），不碰磁盘或 NBT。</p>
 *
 * <p>每个点都是一个带贴图的小四边形：顶面 4 个，四个侧面各 4 个（底面不画），
 * 因此从任意方向都能看到。顶点全亮（{@link LightTexture#FULL_BRIGHT}），
 * 使用 {@link RenderType#entityTranslucent(ResourceLocation)} 做 alpha 混合。</p>
 */
public class SplitComponentRenderer implements BlockEntityRenderer<CraftingBlockEntity> {

    /** 16x16 透明背景 + 中心柔和白点。 */
    private static final ResourceLocation DOT_TEXTURE = new ResourceLocation(
            "ae2_split_cpu", "textures/block/split_dot.png");

    /** 纹理只在第一次渲染时创建，避免过早触碰渲染管线状态。 */
    private static RenderType dotRenderType;

    /** 每个面上 4 个点在面内的 2D 偏移（面中心为原点，单位=方块）。 */
    private static final float[][] DOT_OFFSETS = {
            {-0.21F, -0.21F},
            {0.21F, -0.21F},
            {0.21F, 0.21F},
            {-0.21F, 0.21F}
    };

    /** 单个四边形的一半边长。 */
    private static final float DOT_HALF_SIZE = 0.075F;

    /** 面中心到方块表面的距离（0.5 + 0.002，避免与方块面 z-fighting）。 */
    private static final float FACE_OFFSET = 0.502F;

    /** 已供能时的闪烁角速度（rad/tick）。 */
    private static final float POWERED_SPEED = 0.38F;
    /** 未供能时的闪烁角速度（rad/tick），约为前者的 1/3，形成“缓慢”脉动。 */
    private static final float UNPOWERED_SPEED = 0.11F;

    private static final float ALPHA_MIN = 0.2F;
    private static final float ALPHA_MAX = 1.0F;

    public SplitComponentRenderer() {
    }

    private static RenderType dotRenderType() {
        if (dotRenderType == null) {
            dotRenderType = RenderType.entityTranslucent(DOT_TEXTURE);
        }
        return dotRenderType;
    }

    @Override
    public void render(CraftingBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || be.isRemoved() || !be.isFormed()) {
            return;
        }

        boolean powered = be.isPowered();
        double time = level.getGameTime() + partialTick;
        VertexConsumer consumer = buffers.getBuffer(dotRenderType());

        // 顶面 + 四个侧面；底面贴着相邻方块，不画。
        for (Direction face : Direction.values()) {
            if (face == Direction.DOWN) {
                continue;
            }
            renderFace(pose, consumer, face, time, powered);
        }
    }

    /**
     * 在指定面上画 4 个闪烁点。做法是把 PoseStack 平移到方块中心、旋转到该面的朝向，
     * 再沿面法线平移 {@link #FACE_OFFSET}，之后所有顶点都写在该面局部坐标的 XY 平面上。
     */
    private static void renderFace(PoseStack pose, VertexConsumer consumer, Direction face,
                                   double time, boolean powered) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        switch (face) {
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            default -> {
                // SOUTH：局部 +Z 已经是世界 +Z，无需旋转。
            }
        }
        pose.translate(0.0F, 0.0F, FACE_OFFSET);

        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();

        float speed = powered ? POWERED_SPEED : UNPOWERED_SPEED;
        float red = powered ? 0.25F : 1.0F;
        float green = powered ? 1.0F : 0.68F;
        float blue = powered ? 0.38F : 0.10F;
        float phaseBase = face.get3DDataValue() * 0.9F;

        for (int i = 0; i < DOT_OFFSETS.length; i++) {
            float centerX = DOT_OFFSETS[i][0];
            float centerY = DOT_OFFSETS[i][1];
            float x0 = centerX - DOT_HALF_SIZE;
            float y0 = centerY - DOT_HALF_SIZE;
            float x1 = centerX + DOT_HALF_SIZE;
            float y1 = centerY + DOT_HALF_SIZE;

            // 正弦脉动：每个点相位错开，alpha 在 0.2~1.0 之间。
            float wave = 0.5F + 0.5F * (float) Math.sin(time * speed + phaseBase + i * 1.7F);
            float alpha = ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * wave;

            addQuad(consumer, matrix, normal, x0, y0, x1, y1, red, green, blue, alpha);
        }

        pose.popPose();
    }

    /** 以面局部坐标 (x0,y0)-(x1,y1) 写一个朝 +Z 的四边形。 */
    private static void addQuad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                                float x0, float y0, float x1, float y1,
                                float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, x0, y0, 0.0F)
                .color(red, green, blue, alpha)
                .uv(0.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
        consumer.vertex(matrix, x1, y0, 0.0F)
                .color(red, green, blue, alpha)
                .uv(1.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
        consumer.vertex(matrix, x1, y1, 0.0F)
                .color(red, green, blue, alpha)
                .uv(1.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
        consumer.vertex(matrix, x0, y1, 0.0F)
                .color(red, green, blue, alpha)
                .uv(0.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
