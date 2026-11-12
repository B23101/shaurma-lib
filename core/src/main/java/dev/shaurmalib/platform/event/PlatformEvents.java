package dev.shaurmalib.platform.event;

import dev.shaurmalib.platform.ref.DamageRef;
import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.ref.ServerRef;
import dev.shaurmalib.render.GuiCanvas;

/**
 * Каталог подій, які ЗОБОВ'ЯЗАНИЙ постити кожен {@code loader:*}. Контракт однаковий для Fabric/Forge/NeoForge —
 * тому вся логіка модулів пишеться один раз і підписується тут.
 */
public final class PlatformEvents {
    /** Глобальна шина бібліотеки. */
    public static final EventBus BUS = new EventBus();

    private PlatformEvents() {}

    public record ServerStarted(ServerRef server) implements Event {}
    public record ServerStopping(ServerRef server) implements Event {}
    public record ServerTick(ServerRef server) implements Event {}
    public record PlayerJoin(PlayerRef player) implements Event {}
    public record PlayerLeave(PlayerRef player) implements Event {}
    /** Кінець тіку серверного гравця (Forge PlayerTickEvent END / Fabric END_SERVER_TICK по гравцях). */
    public record ServerPlayerTick(PlayerRef player) implements Event {}
    public record ClientTick() implements Event {}
    /** Початок тіку локального гравця (клієнт). */
    public record ClientPlayerTick(PlayerRef player) implements Event {}
    /** Клієнт вийшов зі світу/сервера. */
    public record ClientDisconnect() implements Event {}
    public record HudRender(GuiCanvas canvas, float partialTick) implements Event {}

    /** Шкода гравцю. Слухач може скасувати або змінити кількість. */
    public static final class PlayerDamage implements Cancellable {
        private final PlayerRef victim; private final DamageRef source;
        private float amount; private boolean cancelled;
        public PlayerDamage(PlayerRef victim, DamageRef source, float amount) {
            this.victim = victim; this.source = source; this.amount = amount;
        }
        public PlayerRef victim() { return victim; }
        public DamageRef source() { return source; }
        public float amount() { return amount; }
        public void amount(float a) { this.amount = a; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void cancel() { cancelled = true; }
    }

    /** Чат-повідомлення гравця до розсилки. */
    public static final class PlayerChat implements Cancellable {
        private final PlayerRef sender; private String text; private boolean cancelled;
        public PlayerChat(PlayerRef sender, String text) { this.sender = sender; this.text = text; }
        public PlayerRef sender() { return sender; }
        public String text() { return text; }
        public void text(String t) { this.text = t; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void cancel() { cancelled = true; }
    }
}
