package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void projectvisual$expandPlayerHitbox(CallbackInfoReturnable<AABB> cir) {
        if (VisualFeatures.isHitboxEnabled()
                && (Object) this instanceof Player player
                && player != Minecraft.getInstance().player) {
            cir.setReturnValue(cir.getReturnValue().inflate(VisualFeatures.getHitboxExpansion()));
        }
    }
}
