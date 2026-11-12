package dev.shaurmalib.common.stamina;

/** Змінний стан stamina одного гравця. Не знає ні про гру, ні про мережу — лише числа. */
public final class StaminaState {
    public static final int UNSET = Integer.MIN_VALUE;

    float value;
    int lastUseTick = UNSET;
    boolean exhausted;

    public StaminaState(float initial) { this.value = initial; }

    public float value() { return value; }
    public boolean exhausted() { return exhausted; }
    public int lastUseTick() { return lastUseTick; }
    public void markUsed(int tick) { this.lastUseTick = tick; }
    public void resetExhausted() { this.exhausted = false; }
}
