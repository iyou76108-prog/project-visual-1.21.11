package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityHitboxMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void projectvisual$playerEsp(CallbackInfoReturnable<Boolean> cir) {
        if (VisualFeatures.isPlayerEsp()
                && (Object) this instanceof Player player
                && player != Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Enlarges client-side living-entity pick boxes while the Hitboxes module is enabled.
     * This affects local targeting/raycasting only; it does not change server hitboxes or reach.
     */
    @Inject(method = "getBoundingBox()Lnet/minecraft/world/phys/AABB;",
            at = @At("RETURN"), cancellable = true)
    private void projectvisual$expandTargetBox(CallbackInfoReturnable<AABB> cir) {
        if (!VisualFeatures.isEnabled("Hitboxes")) return;
        if (!((Object) this instanceof LivingEntity living)
                || living == Minecraft.getInstance().player
                || !living.isAlive()) return;

        cir.setReturnValue(cir.getReturnValue().inflate(VisualFeatures.hitboxExpansion()));
    }

}
