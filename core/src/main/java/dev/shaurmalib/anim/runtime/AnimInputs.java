package dev.shaurmalib.anim.runtime;

/**
 * Знімок стану сутності, з якого рушій сам вирішує локомоцію. Заповнює адаптер версії
 * (поля однакові для гравця, моба чи geo-маньяка — тому рушій не знає, що таке {@code Player}).
 *
 * Тікові поля оновлюються в {@code tick}, кадрові ({@link #limbSwing} та ін.) — перед {@code evaluate}.
 */
public final class AnimInputs {

    // ── тік ──
    public boolean onGround = true, sneaking, sprinting, swimming, crawling, riding, climbing, flying, inWater;
    public boolean usingItem, swinging, sleeping, dead, localPlayer;
    public float verticalSpeed, horizontalSpeed, fallDistance;
    public int hurtTime, ageTicks;
    public String mainHandItem = "", offHandItem = "";

    // ── кадр ──
    /** Пройдена «відстань кроків» (ваніль {@code walkAnimation.position}). Веде фазу ходьби. */
    public float limbSwing;
    /** 0..1 — інтенсивність кроку (ваніль {@code walkAnimation.speed}). Веде змішування idle↔walk. */
    public float limbSwingAmount;
    /** Поворот голови відносно тіла, градуси. */
    public float netHeadYaw, headPitch;
}
