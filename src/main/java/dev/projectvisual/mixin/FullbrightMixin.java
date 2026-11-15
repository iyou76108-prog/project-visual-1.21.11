package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public abstract class FullbrightMixin {
    @Inject(method = "getBrightness(FI)F", at = @At("HEAD"), cancellable = true)
    private static void projectvisual$fullbright(float ambientLight, int lightLevel,
                                                  CallbackInfoReturnable<Float> cir) {
        if (VisualFeatures.isEnabled("Fullbright")) {
            cir.setReturnValue(1.0F);
        }
    }
}
