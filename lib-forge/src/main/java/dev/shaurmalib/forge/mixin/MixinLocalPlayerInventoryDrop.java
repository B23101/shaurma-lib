package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.network.packets.InventorySlotAllocationPacket;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the client drop action before Minecraft sends the drop packet. This
 * keeps the client inventory visually unchanged when the allocation rejects it.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerInventoryDrop {

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void shaurma$blockDrop(boolean dropAll, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (InventorySlotAllocationPacket.ClientHandler.isItemDropBlocked()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
