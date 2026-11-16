package dev.projectvisual.mixin;

import dev.projectvisual.client.hud.VisualHud;
import dev.projectvisual.client.hud.HudWindowManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseClickMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"))
    private void projectvisual$hudWindowButtons(long window, MouseButtonInfo buttonInfo, int action,
                                                  CallbackInfo ci) {
        HudWindowManager.onMouseButton(buttonInfo.button(), action == GLFW.GLFW_PRESS);
    }

    @Inject(method = "onMove(JDD)V", at = @At("TAIL"))
    private void projectvisual$hudWindowDrag(long window, double x, double y, CallbackInfo ci) {
        HudWindowManager.onMouseMove(x, y);
    }

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("TAIL"))
    private void projectvisual$countClicks(long window, MouseButtonInfo buttonInfo, int action,
                                            CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            VisualHud.recordMouseClick(buttonInfo.button());
        }
    }
}
