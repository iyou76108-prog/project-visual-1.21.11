package dev.projectvisual.mixin;

import dev.projectvisual.client.training.TrainingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class TrainingCombatMixin {
    @Unique
    private boolean projectvisual$placingCobweb;

    @Inject(
            method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD")
    )
    private void projectvisual$trackPvpAttack(Player player, Entity target, CallbackInfo ci) {
        TrainingManager.onAttack(player, target);
    }

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD")
    )
    private void projectvisual$rememberCobweb(LocalPlayer player, InteractionHand hand,
                                               BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        projectvisual$placingCobweb = player == Minecraft.getInstance().player
                && player.getItemInHand(hand).is(Items.COBWEB);
    }

    @Inject(
            method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN")
    )
    private void projectvisual$countCobweb(LocalPlayer player, InteractionHand hand,
                                            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (projectvisual$placingCobweb && cir.getReturnValue() != null
                && cir.getReturnValue().consumesAction()) {
            TrainingManager.recordCobwebPlacement();
        }
        projectvisual$placingCobweb = false;
    }
}
