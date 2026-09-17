package dev.shaurmalib.forge.chat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Серверні хуки чат-модуля: без них консюмер мусив би не забути викликати
 * {@link ChatModule#syncChannels} при кожному вході — а забути це легко,
 * і тоді в T-екрані не було б кнопок каналів.
 *
 * <p>Зміни ролей/фаз консюмер синхронізує сам (він єдиний знає, коли
 * вони змінились).</p>
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class ChatHooks {

    private ChatHooks() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatModule.syncChannels(player);
        }
    }
}
