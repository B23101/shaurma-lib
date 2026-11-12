package dev.shaurmalib.common.stamina;

import dev.shaurmalib.platform.ref.PlayerRef;

/**
 * Єдина точка для тонких міксинів {@code mc:*} ({@code setSprinting}, {@code jumpFromGround}).
 * Міксин на кожній версії — 3 рядки: {@code StaminaGates.blockSprint(wrap(player), player.level().isClientSide)}.
 * Вибір «сервер чи клієнт» — тут, а не в кожному міксині.
 */
public final class StaminaGates {
    private static volatile StaminaService server;
    private static volatile StaminaClientState client;
    private static volatile java.util.function.Predicate<PlayerRef> isLocalPlayer = p -> false;

    private StaminaGates() {}

    public static void bind(StaminaService s, StaminaClientState c, java.util.function.Predicate<PlayerRef> local) {
        server = s; client = c; isLocalPlayer = local == null ? p -> false : local;
    }
    public static void unbind() { server = null; client = null; isLocalPlayer = p -> false; }

    public static boolean blockSprint(PlayerRef p, boolean clientSide) {
        if (clientSide) { StaminaClientState c = client; return c != null && isLocalPlayer.test(p) && c.shouldBlockSprint(); }
        StaminaService s = server; return s != null && s.shouldBlockSprint(p);
    }

    public static boolean blockJump(PlayerRef p, boolean clientSide) {
        if (clientSide) { StaminaClientState c = client; return c != null && isLocalPlayer.test(p) && c.shouldBlockJump(); }
        StaminaService s = server; return s != null && s.shouldBlockJump(p);
    }
}
