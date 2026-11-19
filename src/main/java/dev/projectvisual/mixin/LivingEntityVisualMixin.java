package dev.projectvisual.mixin;

import dev.projectvisual.client.ProjectVisualClient;
import dev.projectvisual.client.VisualFeatures;
import dev.projectvisual.client.VisualWorldEffects;
import dev.projectvisual.client.hud.VisualHud;
import dev.projectvisual.client.training.TrainingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityVisualMixin {
    @Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"))
    private void projectvisual$onEntityEvent(byte status, CallbackInfo ci) {
        if (status != 2 && status != 35) return;

        LivingEntity entity = (LivingEntity) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || entity.level() != client.level) return;

        if (status == 2) {
            TrainingManager.onHurtAnimation(entity);
            if (entity == client.player && VisualFeatures.isEnabled("Damage Tint")) {
                VisualHud.triggerDamageFlash();
            }
            if (entity != client.player && VisualFeatures.isEnabled("Hit Particles")) {
                VisualWorldEffects.spawnHitParticles(entity);
            }
        }

        if (status == 35 && entity == client.player
                && VisualFeatures.isEnabled("Totem Counter")) {
            VisualHud.recordTotemPop();
        }
    }
}
