package dev.projectvisual.mixin;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonVisualMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void projectvisual$animateFirstPerson(AbstractClientPlayer player, float partialTick,
            float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
            float equippedProgress, PoseStack matrices, SubmitNodeCollector submitNodeCollector,
            int packedLight, CallbackInfo ci) {
        float side = hand == InteractionHand.MAIN_HAND ? 1.0F : -1.0F;

        if (VisualFeatures.isEnabled("View Model")) {
            matrices.translate(side * 0.045D, -0.025D, 0.035D);
            matrices.scale(0.93F, 0.93F, 0.93F);
        }

        if (VisualFeatures.isEnabled("Old Animations")) {
            matrices.translate(side * 0.025D, 0.035D, 0.015D);
            matrices.mulPose(Axis.XP.rotationDegrees(-7.0F));
            matrices.mulPose(Axis.YP.rotationDegrees(side * 7.0F));
        }

        if (VisualFeatures.isEnabled("Swing Animation") && swingProgress > 0.0F) {
            float swing = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
            matrices.mulPose(Axis.ZP.rotationDegrees(side * swing * 12.0F));
            matrices.mulPose(Axis.YP.rotationDegrees(side * swing * 8.0F));
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
}
