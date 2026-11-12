package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.inventory.InventorySlotAllocation;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void shaurma$restrictSelectedSlot(ServerboundSetCarriedItemPacket packet,
                                               CallbackInfo callbackInfo) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (packet.getSlot() < 0 || packet.getSlot() > 8
                || InventorySlotAllocation.isSlotAllowed(listener.player, packet.getSlot())) {
            return;
        }

        callbackInfo.cancel();
    }
}
