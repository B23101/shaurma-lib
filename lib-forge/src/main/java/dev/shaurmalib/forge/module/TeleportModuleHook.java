package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.teleport.TeleportService;

/**
 * Точка вбудовування сервісу телепортації (план, п. 3.2) —
 * {@link TeleportService} (пакет {@code dev.shaurmalib.forge.teleport}).
 * Підключається через {@code ShaurmaLib.Builder.withTeleport()} на
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder} — статичний сервіс без
 * стану, {@code withTeleport()} лише прапорець свідомого підключення
 * (потрібен для {@code withSpectator()}/{@code withFreeCamera()}, які
 * повертають гравця через {@link TeleportService} при виході з режиму).
 * <p>
 * Перенесено з {@code org.example.snipers_shaurma.core.util.TeleportUtil}
 * буквально по сигнатурах (заміна імпорту в споживачі не змінює жодного
 * виклик-сайту), розширено п'ятьма пунктами плану: chunk-guarantee
 * (force-load цільового чанка через {@code TicketType.PORTAL}),
 * fall-distance reset, cross-dimension safety, passenger cascade і
 * post-teleport {@link dev.shaurmalib.forge.teleport.TeleportedEvent}
 * через {@code MinecraftForge.EVENT_BUS} — усі п'ять реалізовані
 * повністю (не stub), дивись клас-докстрінг {@link TeleportService} для
 * деталей кожного розширення.
 */
public interface TeleportModuleHook {
    void onAttach(FMLModuleContext ctx);
}
