package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import dev.projectvisual.client.VisualWorldEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ZoomMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
    private void projectvisual$zoom(Camera camera, float tickProgress, boolean changingFov,
                                    CallbackInfoReturnable<Float> cir) {
        if (VisualFeatures.isEnabled("Zoom") && VisualWorldEffects.isZoomKeyDown()) {
            cir.setReturnValue(cir.getReturnValue() * 0.25F);
        }
    }
}
