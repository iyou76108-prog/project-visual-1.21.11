package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class PlayerNameTagsMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z",
            at = @At("RETURN"), cancellable = true)
    private void projectvisual$showPlayerNames(Entity entity, double distanceSquared,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (VisualFeatures.isEnabled("Name Tags")
                && entity instanceof Player
                && entity != Minecraft.getInstance().player
                && distanceSquared <= 64.0D * 64.0D) {
            cir.setReturnValue(true);
        }
    }
}
