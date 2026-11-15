package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraClipMixin {
    @Inject(method = "clipToSpace(F)F", at = @At("RETURN"), cancellable = true)
    private void projectvisual$disableCameraCollision(float requestedDistance,
                                                       CallbackInfoReturnable<Float> cir) {
        if (VisualFeatures.isEnabled("Camera Clip")) {
            cir.setReturnValue(requestedDistance);
        }
    }
}
