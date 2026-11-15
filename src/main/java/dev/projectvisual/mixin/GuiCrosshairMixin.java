package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiCrosshairMixin {
    @Inject(
        method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void projectvisual$replaceVanillaCrosshair(GuiGraphics graphics,
                                                         DeltaTracker tickCounter,
                                                         CallbackInfo ci) {
        if (VisualFeatures.isEnabled("Crosshair") || VisualFeatures.isEnabled("Hit Color")) {
            ci.cancel();
        }
    }
}
