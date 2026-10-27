package com.jason54.itemrush.mixin;

import com.jason54.itemrush.game.GameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects successful ItemEntity pickups (server-side) for ItemRush.
 * Hooks {@link Player#take} which only runs after the stack was added to the inventory.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Unique
    private ItemStack itemrush$pendingStack = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void itemrush$snapshotStack(Player player, CallbackInfo ci) {
        if (player.level().isClientSide() || !GameManager.isAcceptingPickups()) {
            itemrush$pendingStack = ItemStack.EMPTY;
            return;
        }
        itemrush$pendingStack = ((ItemEntity) (Object) this).getItem().copy();
    }

    @Inject(
            method = "playerTouch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"
            )
    )
    private void itemrush$onSuccessfulPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (itemrush$pendingStack.isEmpty()) {
            return;
        }
        ItemEntity self = (ItemEntity) (Object) this;
        GameManager.onItemPickedUp(serverPlayer, self, itemrush$pendingStack);
        itemrush$pendingStack = ItemStack.EMPTY;
    }
}
