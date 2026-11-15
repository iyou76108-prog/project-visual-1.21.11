package dev.projectvisual.mixin;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemPhysicsMixin {
    @Inject(method = "getSpin(FF)F", at = @At("RETURN"), cancellable = true)
    private static void projectvisual$slowItemSpin(float age, float tickProgress,
                                                    CallbackInfoReturnable<Float> cir) {
        if (VisualFeatures.isEnabled("Item Physics")) {
            cir.setReturnValue(cir.getReturnValue() * 0.45F);
        }
    }
}
