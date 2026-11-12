package dev.shaurmalib.forge.client.chat;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Перехоплює відкриття ванільного {@code ChatScreen} (клавіша T / "/"
 * за замовч.) і підміняє його бібліотечним {@link ChatHistoryScreen}
 * (план, п. 3.9 + 3.29). Перенесення {@code ChatScreenInterceptHandler}
 * snipers_shaurma, узагальнене: замість {@code @Mod.EventBusSubscriber(modid = "snipers_shaurma", ...)}
 * (жорсткий modId одного мода) — звичайний метод, підписаний з боку
 * консюмера на його власний {@code @SubscribeEvent}, або через
 * {@link dev.shaurmalib.forge.chat.ChatModule#registerScreenIntercept}.
 * <p>
 * Постачальники передаються з {@code ChatModule.attach(...)} — бібліотека
 * не знає нічого про {@code ClientGameState}/{@code CustomTabOverlay}
 * конкретного мода.
 */
public final class ChatScreenInterceptHandler {

    private ChatScreenInterceptHandler() {}

    public static void onScreenOpening(ScreenEvent.Opening event,
                                        BooleanSupplier teamModeAvailable,
                                        Consumer<Boolean> tabOverlayForceVisible) {
        if (event.getScreen() instanceof ChatScreen) {
            event.setNewScreen(new ChatHistoryScreen(teamModeAvailable, tabOverlayForceVisible));
        }
    }
}
