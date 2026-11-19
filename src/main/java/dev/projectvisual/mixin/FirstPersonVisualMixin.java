package dev.projectvisual.mixin;

import com.mojang.math.Axis;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import dev.projectvisual.client.ProjectVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonVisualMixin {
    private static final RenderPipeline SWORD_PIPELINE =
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "pipeline/held_sword"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withoutBlend()
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .build();

    private static final RenderType SWORD_RENDER_TYPE = RenderType.create(
            "project_visual_held_sword",
            RenderSetup.builder(SWORD_PIPELINE).createRenderSetup());


    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void projectvisual$animateFirstPerson(AbstractClientPlayer player, float partialTick,
            float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
            float equippedProgress, PoseStack matrices, SubmitNodeCollector submitNodeCollector,
            int packedLight, CallbackInfo ci) {
        float side = hand == InteractionHand.MAIN_HAND ? 1.0F : -1.0F;

        if (VisualFeatures.isEnabled("View Model")) {
            matrices.translate(side * 0.045D, -0.025D, 0.035D);
            matrices.scale(VisualFeatures.viewModelScale(), VisualFeatures.viewModelScale(), VisualFeatures.viewModelScale());
        }

        if (VisualFeatures.isEnabled("Old Animations")) {
            matrices.translate(side * 0.025D, 0.035D, 0.015D);
            matrices.mulPose(Axis.XP.rotationDegrees(-7.0F));
            matrices.mulPose(Axis.YP.rotationDegrees(side * 7.0F));
        }

        if (VisualFeatures.isEnabled("Swing Animation") && swingProgress > 0.0F) {
            float eased = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
            float swing = eased * VisualFeatures.swingStrength();
            switch (VisualFeatures.swingStyle()) {
                case 0 -> {
                    matrices.mulPose(Axis.ZP.rotationDegrees(side * swing * 12.0F));
                    matrices.mulPose(Axis.YP.rotationDegrees(side * swing * 8.0F));
                }
                case 1 -> {
                    matrices.mulPose(Axis.XP.rotationDegrees(-swing * 10.0F));
                    matrices.mulPose(Axis.ZP.rotationDegrees(side * swing * 17.0F));
                    matrices.mulPose(Axis.YP.rotationDegrees(side * swing * 5.0F));
                }
                case 2 -> {
                    matrices.mulPose(Axis.ZP.rotationDegrees(side * swing * 22.0F));
                    matrices.mulPose(Axis.YP.rotationDegrees(side * swing * 16.0F));
                    matrices.mulPose(Axis.XP.rotationDegrees(-swing * 7.0F));
                }
                case 3 -> {
                    float snap = (float) Math.pow(eased, 0.45D);
                    matrices.mulPose(Axis.YP.rotationDegrees(-side * snap * 15.0F * VisualFeatures.swingStrength()));
                    matrices.mulPose(Axis.ZP.rotationDegrees(side * snap * 9.0F * VisualFeatures.swingStrength()));
                }
                default -> { }
            }
        }

        if (VisualFeatures.isEnabled("Item Sway")) {
            float phase = (float) (System.currentTimeMillis() % 8000L) / 8000.0F
                    * (float) (Math.PI * 2.0D);
            matrices.translate(Math.sin(phase) * 0.012D, Math.cos(phase * 0.7F) * 0.008D, 0.0D);
            matrices.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(phase) * 1.2F));
        }

        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            ItemUseAnimation animation = stack.getUseAnimation();

            if (VisualFeatures.isEnabled("Eating Animation")
                    && (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK)) {
                float phase = (player.getTicksUsingItem() + partialTick) * 0.22F;
                matrices.translate(0.0D, Math.sin(phase) * 0.018D, -0.012D);
                matrices.mulPose(Axis.XP.rotationDegrees((float) Math.sin(phase) * 2.2F));
            }

            if (VisualFeatures.isEnabled("Shield Animation")
                    && stack.is(Items.SHIELD) && animation == ItemUseAnimation.BLOCK) {
                matrices.translate(side * -0.045D, 0.025D, -0.055D);
                matrices.mulPose(Axis.YP.rotationDegrees(side * 11.0F));
            }
        }

        if (VisualFeatures.isEnabled("Rod Animation") && stack.is(Items.FISHING_ROD)) {
            matrices.translate(side * 0.025D, 0.015D, -0.035D);
            matrices.mulPose(Axis.XP.rotationDegrees(-4.0F));
        }
    }
    @Inject(
            method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void projectvisual$replaceFirstPersonSword(LivingEntity entity, ItemStack stack,
            ItemDisplayContext displayContext, PoseStack matrices,
            SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        if (displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        boolean pencil = VisualFeatures.isEnabled("Pencil Sword");
        boolean rapier = VisualFeatures.isEnabled("Divine Rapier");
        if ((!pencil && !rapier) || !isVanillaSword(stack)) return;

        // This hook runs while ItemInHandRenderer still has the first-person hand/equip matrices.
        matrices.pushPose();
        matrices.scale(0.42F, 0.42F, 0.42F);
        matrices.mulPose(Axis.XP.rotationDegrees(-8.0F));
        collector.submitCustomGeometry(matrices, SWORD_RENDER_TYPE,
                (pose, out) -> renderHeldSword(out, pose, pencil));
        matrices.popPose();
        ci.cancel();
    }

    private static boolean isVanillaSword(ItemStack stack) {
        return stack.is(Items.WOODEN_SWORD) || stack.is(Items.STONE_SWORD)
                || stack.is(Items.IRON_SWORD) || stack.is(Items.GOLDEN_SWORD)
                || stack.is(Items.DIAMOND_SWORD) || stack.is(Items.NETHERITE_SWORD);
    }

    private static void renderHeldSword(VertexConsumer out, PoseStack.Pose pose, boolean pencil) {
        // Item-local geometry: the hilt sits near y=0; the blade runs toward +Y.
        box(out, pose, -0.055F, -0.08F, -0.035F, 0.055F, 0.10F, 0.035F,
                pencil ? 0xFF6B4129 : 0xFF49382D);
        if (pencil) {
            box(out, pose, -0.070F, 0.08F, -0.045F, 0.070F, 0.17F, 0.045F, 0xFFE97991);
            box(out, pose, -0.082F, 0.17F, -0.052F, 0.082F, 0.25F, 0.052F, 0xFFB9C2CB);
            box(out, pose, -0.065F, 0.25F, -0.045F, 0.065F, 1.50F, 0.045F, 0xFFFFC928);
            box(out, pose, -0.070F, 0.30F, 0.045F, -0.045F, 1.45F, 0.055F, 0xFFE84A43);
            box(out, pose, 0.045F, 0.30F, 0.045F, 0.070F, 1.45F, 0.055F, 0xFFE84A43);
            box(out, pose, -0.025F, 0.28F, -0.052F, 0.025F, 1.48F, -0.045F, 0xFFFFE58B);
            cone(out, pose, 0.0F, 1.50F, 1.76F, 0.070F, 0xFFFFD9AB);
            cone(out, pose, 0.0F, 1.72F, 1.84F, 0.026F, 0xFF26242A);
        } else {
            box(out, pose, -0.045F, 0.10F, -0.035F, 0.045F, 0.29F, 0.035F, 0xFF8A6544);
            box(out, pose, -0.22F, 0.28F, -0.045F, 0.22F, 0.33F, 0.045F, 0xFFD9B45F);
            box(out, pose, -0.045F, 0.30F, -0.040F, 0.045F, 0.39F, 0.040F, 0xFFFFE4A1);
            box(out, pose, -0.032F, 0.35F, -0.025F, 0.032F, 1.65F, 0.025F, 0xFFDCEAF5);
            box(out, pose, -0.012F, 0.42F, 0.026F, 0.012F, 1.58F, 0.032F, 0xFF8DE7FF);
            box(out, pose, -0.045F, 0.39F, -0.035F, 0.045F, 1.60F, -0.025F, 0xFFAABDD2);
            sphere(out, pose, 0.0F, 0.02F, 0.0F, 0.055F, 0xFF55C8E8);
        }
    }

    private static void box(VertexConsumer out, PoseStack.Pose pose,
                            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        quad(out, pose, color, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        quad(out, pose, color, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        quad(out, pose, color, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
        quad(out, pose, color, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        quad(out, pose, color, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        quad(out, pose, color, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, int color,
                             float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz) {
        out.addVertex(pose, ax,ay,az).setColor(color);
        out.addVertex(pose, bx,by,bz).setColor(color);
        out.addVertex(pose, cx,cy,cz).setColor(color);
        out.addVertex(pose, dx,dy,dz).setColor(color);
    }

    private static void cone(VertexConsumer out, PoseStack.Pose pose,
                             float x, float z, float y0, float radius, int color) {
        int segments = 8;
        float y1 = y0 + radius * 2.2F;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            float ax = x + (float) Math.cos(a0) * radius;
            float az = z + (float) Math.sin(a0) * radius;
            float bx = x + (float) Math.cos(a1) * radius;
            float bz = z + (float) Math.sin(a1) * radius;
            quad(out, pose, color, ax,y0,az, bx,y0,bz, x,y1,z, x,y1,z);
        }
    }

    private static void sphere(VertexConsumer out, PoseStack.Pose pose,
                               float x, float y, float z, float radius, int color) {
        // Faceted pommel built from three intersecting bands.
        box(out, pose, x-radius, y-radius*0.5F, z-radius,
                x+radius, y+radius*0.5F, z+radius, color);
        box(out, pose, x-radius*0.5F, y-radius, z-radius*0.5F,
                x+radius*0.5F, y+radius, z+radius*0.5F, 0xFFB4ECF7);
    }

}
