package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.inventory.InventorySlotAllocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void shaurma$restrictInventoryClick(int slotId, int button, ClickType clickType,
                                                 Player player, CallbackInfo callbackInfo) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!InventorySlotAllocation.shouldCancelClick(menu, slotId, button, clickType, serverPlayer)) {
            return;
        }

        menu.sendAllDataToRemote();
        callbackInfo.cancel();
    }
}
