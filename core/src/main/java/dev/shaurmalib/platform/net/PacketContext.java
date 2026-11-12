package dev.shaurmalib.platform.net;

import dev.shaurmalib.platform.ref.PlayerRef;
import java.util.Optional;

/** Контекст обробки: відправник (лише C2S) і перенесення в головний потік гри. */
public interface PacketContext {
    Optional<PlayerRef> sender();
    void enqueue(Runnable r);
}
