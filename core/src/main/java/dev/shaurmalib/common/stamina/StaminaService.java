package dev.shaurmalib.common.stamina;

import dev.shaurmalib.common.net.StaminaSyncPacket;
import dev.shaurmalib.platform.ref.PlayerRef;
import dev.shaurmalib.platform.service.NetworkSender;
import dev.shaurmalib.platform.service.PlayerOps;
import dev.shaurmalib.platform.service.PlayerPersistence;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверний оркестратор stamina. Раніше — статичний клас, що напряму брав {@code ServerPlayer}/{@code Mth}/NBT/мережу Forge.
 * Тепер залежності передаються в конструктор (інтерфейси SPI), тож сервіс тестується фейками, а нова версія гри
 * не змінює в ньому жодного рядка.
 */
public final class StaminaService {
    public static final String PERSIST_KEY = "shaurma_lib_stamina";

    private final PlayerOps ops;
    private final PlayerPersistence store;
    private final NetworkSender net;

    private final Map<UUID, StaminaRules> rules = new ConcurrentHashMap<>();
    private final Map<UUID, StaminaState> states = new ConcurrentHashMap<>();
    private final Map<UUID, StaminaSyncPacket> lastSync = new ConcurrentHashMap<>();
    private volatile StaminaRules defaults = StaminaRules.defaults();
    private volatile boolean enabled;

    public StaminaService(PlayerOps ops, PlayerPersistence store, NetworkSender net) {
        this.ops = Objects.requireNonNull(ops);
        this.store = Objects.requireNonNull(store);
        this.net = Objects.requireNonNull(net);
    }

    public void enable(StaminaRules defaultRules) {
        enabled = true;
        if (defaultRules != null) defaults = defaultRules;
    }
    public boolean isEnabled() { return enabled; }

    // ---- правила ----
    public void setRules(PlayerRef p, StaminaRules r) {
        requireEnabled();
        if (p == null || r == null) throw new IllegalArgumentException("Гравець і правила stamina не можуть бути null.");
        StaminaRules prev = rules.put(p.uuid(), r);
        float old = getStamina(p);
        if (prev == null || prev.active() != r.active()) {
            state(p).markUsed(ops.tickCount(p));
            state(p).resetExhausted();
        }
        setStamina(p, Math.min(old, r.maxStamina()), false);
        sync(p, false);
    }

    public StaminaRules getRules(PlayerRef p) {
        if (p == null) return defaults;
        StaminaRules r = rules.get(p.uuid());
        return r != null ? r : defaults;
    }

    // ---- значення ----
    public float getStamina(PlayerRef p) {
        return p == null ? 0 : state(p).value;
    }

    public float getMaxStamina(PlayerRef p) { return getRules(p).maxStamina(); }

    public void setStamina(PlayerRef p, float value) { setStamina(p, value, true); }

    public void setStamina(PlayerRef p, float value, boolean resetRecoveryDelay) {
        requireEnabled();
        if (p == null || !Float.isFinite(value)) throw new IllegalArgumentException("Гравець і stamina мають бути коректними.");
        StaminaRules r = getRules(p);
        StaminaState s = state(p);
        float old = s.value;
        s.value = StaminaEngine.clamp(value, 0, r.maxStamina());
        persist(p, s);
        StaminaEngine.updateExhausted(r, s, s.value);
        if (resetRecoveryDelay && s.value < old) s.markUsed(ops.tickCount(p));
        sync(p, false);
    }

    public void setStaminaPercent(PlayerRef p, float percent) {
        if (!Float.isFinite(percent)) throw new IllegalArgumentException("Відсоток stamina має бути скінченним числом.");
        setStamina(p, getRules(p).maxStamina() * StaminaEngine.clamp(percent, 0, 1));
    }

    // ---- запити для міксинів ----
    public boolean isActive(PlayerRef p) {
        return enabled && p != null && getRules(p).active() && !ops.isCreativeOrSpectator(p);
    }
    public boolean shouldBlockJump(PlayerRef p) {
        return isActive(p) && getRules(p).blockJumpWhenDepleted() && getStamina(p) <= 0;
    }
    public boolean shouldBlockSprint(PlayerRef p) {
        return isActive(p) && (getStamina(p) <= 0 || state(p).exhausted);
    }

    // ---- життєвий цикл ----
    public void syncNow(PlayerRef p) { requireEnabled(); sync(Objects.requireNonNull(p), true); }

    public void onLogin(PlayerRef p) { if (enabled) sync(p, true); }

    public void clear(PlayerRef p) {
        if (p == null) return;
        forget(p.uuid());
        store.remove(p, PERSIST_KEY);
        net.sendToPlayer(p, StaminaSyncPacket.disabled());
    }

    public void onLogout(PlayerRef p) { if (p != null) forget(p.uuid()); }

    /** Кінець тіку серверного гравця. */
    public void tick(PlayerRef p) {
        if (!enabled) return;
        StaminaRules r = getRules(p);
        if (ops.isCreativeOrSpectator(p) || !r.active()) { sync(p, false); return; }

        StaminaState s = state(p);
        StaminaEngine.Result res = StaminaEngine.step(r, s,
                new StaminaEngine.Input(ops.tickCount(p), ops.isSprinting(p), ops.isRiding(p)));
        if (res.stopSprinting()) ops.setSprinting(p, false);
        persist(p, s);
        if (r.forceFullHungerWhileActive()) ops.setFood(p, 20, 5.0f);
        sync(p, false);
    }

    // ---- внутрішнє ----
    private StaminaState state(PlayerRef p) {
        return states.computeIfAbsent(p.uuid(), id -> {
            float max = getRules(p).maxStamina();
            float v = store.getNumber(p, PERSIST_KEY).isPresent() ? (float) store.getNumber(p, PERSIST_KEY).getAsDouble() : max;
            return new StaminaState(StaminaEngine.clamp(v, 0, max));
        });
    }

    private void persist(PlayerRef p, StaminaState s) { store.putNumber(p, PERSIST_KEY, s.value); }

    private void forget(UUID id) { rules.remove(id); states.remove(id); lastSync.remove(id); }

    private void sync(PlayerRef p, boolean force) {
        StaminaRules r = getRules(p);
        boolean active = r.active() && !ops.isCreativeOrSpectator(p);
        StaminaSyncPacket pkt = new StaminaSyncPacket(active, getStamina(p), r.maxStamina(),
                r.blockJumpWhenDepleted(), active && shouldBlockSprint(p));
        if (!force && pkt.equals(lastSync.get(p.uuid()))) return;
        lastSync.put(p.uuid(), pkt);
        net.sendToPlayer(p, pkt);
    }

    private void requireEnabled() {
        if (!enabled) throw new IllegalStateException("Stamina не підключена — додайте StaminaModule.");
    }
}
