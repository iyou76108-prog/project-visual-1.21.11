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

    /**
     * Meteor-style target expansion: Entity#getPickRadius is consulted by the
     * client's entity ray-picking code. Only other live players are affected.
     * This is client-side target selection, not a server-side reach change.
     */
    @Inject(method = "getPickRadius()F", at = @At("HEAD"), cancellable = true)
    private void projectvisual$expandPlayerPickRadius(CallbackInfoReturnable<Float> cir) {
        if (!VisualFeatures.isEnabled("Hitboxes")) return;
        if (!((Object) this instanceof Player target)
                || target == Minecraft.getInstance().player
                || !target.isAlive()) return;
        cir.setReturnValue((float) VisualFeatures.hitboxExpansion());
    }

}
