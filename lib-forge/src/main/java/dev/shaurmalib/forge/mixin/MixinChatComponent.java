package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.chat.ChatModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MixinChatComponent — перехоплює ВСІ вхідні чат-повідомлення (системні,
 * команди, ванільний чат) і додає їх у бібліотечний {@link ChatModule}
 * (план, п. 3.9 + 3.29). Перенесено 1:1 з
 * {@code core.mixin.MixinChatComponent} snipers_shaurma.
 * <p>
 * Оскільки лібовий {@code AlertNotificationSystem}-фід — єдиний видимий
 * фід повідомлень (vanilla chat panel типово приховано консюмером через
 * власний HUD-cancel механізм), без цього міксину системні повідомлення
 * та вивід команд були б невидимі для гравця.
 * {@link dev.shaurmalib.forge.network.packets.ChatBroadcastPacket} додає
 * свої повідомлення напряму через {@link ChatModule#onBroadcastReceived},
 * НЕ через {@code ChatComponent.addMessage}, тому дублікатів не виникає.
 * <p>
 * Активується лише разом з реєстрацією миксин-класу в
 * {@code shaurma_lib.mixins.json} — на відміну від решти статичних
 * рушіїв бібліотеки, миксин завжди присутній у jar-і (Forge mixin
 * підключається до завантаження класу), тому підключення чат-модуля
 * консюмером (через {@code withChat(...)}) не вимикає сам миксин, а
 * лише робить {@link ChatModule#onSystemMessage} безрезультатним, якщо
 * жоден фід не зареєстровано.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(ChatComponent.class)
public class MixinChatComponent {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void shaurmaLib$onAddMessage(Component message, CallbackInfo ci) {
        ChatModule.onSystemMessage(message);
    }
}
