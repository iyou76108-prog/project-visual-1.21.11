package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ClientWorldVisualMixin {
    @Inject(method = "getRainLevel(F)F", at = @At("HEAD"), cancellable = true)
    private void projectvisual$clearRain(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide() && VisualFeatures.isEnabled("Weather")) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "getThunderLevel(F)F", at = @At("HEAD"), cancellable = true)
    private void projectvisual$clearThunder(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide() && VisualFeatures.isEnabled("Weather")) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "getDayTime()J", at = @At("RETURN"), cancellable = true)
    private void projectvisual$localTime(CallbackInfoReturnable<Long> cir) {
        if (!((Level) (Object) this).isClientSide() || !VisualFeatures.isEnabled("Time Changer")) return;
        long time = cir.getReturnValue();
        cir.setReturnValue(time - Math.floorMod(time, 24000L) + 6000L);
    }
}
