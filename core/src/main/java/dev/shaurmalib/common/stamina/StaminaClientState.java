package dev.shaurmalib.common.stamina;

import dev.shaurmalib.common.net.StaminaSyncPacket;

/** Клієнтське дзеркало стану stamina (джерело правди — сервер, див. {@link StaminaSyncPacket}). */
public final class StaminaClientState {
    private volatile StaminaSyncPacket last = StaminaSyncPacket.disabled();

    public void apply(StaminaSyncPacket p) { last = p; }
    public void clear() { last = StaminaSyncPacket.disabled(); }

    public boolean isActive() { return last.active(); }
    public float stamina() { return last.stamina(); }
    public float maxStamina() { return last.maxStamina(); }
    public boolean shouldBlockJump() { StaminaSyncPacket p = last; return p.active() && p.blockJumpWhenDepleted() && p.stamina() <= 0; }
    public boolean shouldBlockSprint() { StaminaSyncPacket p = last; return p.active() && p.sprintBlocked(); }
}
