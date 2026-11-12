package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.ref.PlayerRef;
import java.util.Optional;

/** Клієнтський доступ до локального гравця (на dedicated server реалізація не потрібна). */
public interface ClientAccess {
    Optional<PlayerRef> localPlayer();
}
