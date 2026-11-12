package dev.shaurmalib.forge.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Подія, що вилітає ПІСЛЯ успішної телепортації через {@link TeleportService}
 * (план, п. 3.2, пункт 5 — "Post-teleport event hook"). Системи на кшталт
 * freecam/spectator/radiation (кожна з яких у snipers_shaurma зараз сама
 * кешує позицію гравця і має пам'ятати скинути кеш після кожного ручного
 * виклику {@code TeleportUtil.teleport(...)}) підписуються на цю подію
 * один раз централізовано, замість дублювання скидання кешу в кожному
 * місці виклику телепорту.
 */
public class TeleportedEvent extends Event {

    private final ServerPlayer player;
    private final ServerLevel from;
    private final ServerLevel to;
    private final TeleportReason reason;
    private final String customReasonDetail;

    public TeleportedEvent(ServerPlayer player, ServerLevel from, ServerLevel to,
                            TeleportReason reason, String customReasonDetail) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.reason = reason;
        this.customReasonDetail = customReasonDetail;
    }

    public ServerPlayer player() {
        return player;
    }

    public ServerLevel from() {
        return from;
    }

    public ServerLevel to() {
        return to;
    }

    public boolean crossedDimension() {
        return from != to;
    }

    public TeleportReason reason() {
        return reason;
    }

    /** Заповнено лише коли {@link #reason()} == {@link TeleportReason#CUSTOM}. */
    public String customReasonDetail() {
        return customReasonDetail;
    }
}
