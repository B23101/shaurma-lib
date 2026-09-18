package dev.shaurmalib.forge.stamina;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.network.packets.StaminaSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaminaService {
    private static final String PERSISTED_STAMINA = "shaurma_lib_stamina";
    private static final Map<UUID, StaminaRules> RULES = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> VALUES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_USE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_SYNC = new ConcurrentHashMap<>();
    private static volatile boolean enabled;

    /**
     * Поріг горизонтальної швидкості (у квадраті — уникаємо sqrt), нижче
     * якого гравець вважається таким, що фактично не рухається, навіть
     * якщо {@code isSprinting()} на цей тік {@code true}. Значення взято
     * помітно нижчим за звичайну ванільну швидкість бігу (~0.2-0.28
     * блок/тік по горизонталі при спринті) — короткий вертикальний
     * стрибок з мінімальним forward-імпульсом під нього не підпадає,
     * реальний спринт-біг завжди підпадає.
     */
    private static final double MIN_SPRINT_MOTION_SQR = 0.01d;

    private StaminaService() {}

    public static void enable(StaminaRules defaults) {
        enabled = true;
        if (defaults != null) {
            defaultRules = defaults;
        }
    }

    private static volatile StaminaRules defaultRules = StaminaRules.defaults();

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setRules(ServerPlayer player, StaminaRules rules) {
        requireEnabled();
        if (player == null || rules == null) {
            throw new IllegalArgumentException("Гравець і правила stamina не можуть бути null.");
        }
        StaminaRules previous = RULES.put(player.getUUID(), rules);
        float oldStamina = getStamina(player);
        if (previous == null || previous.active() != rules.active()) {
            LAST_USE_TICK.put(player.getUUID(), player.tickCount);
        }
        setStamina(player, Math.min(oldStamina, rules.maxStamina()), false);
        sync(player);
    }

    public static StaminaRules getRules(Player player) {
        if (player == null) return defaultRules;
        StaminaRules rules = RULES.get(player.getUUID());
        if (rules != null) return rules;
        return defaultRules;
    }

    public static float getStamina(Player player) {
        if (player == null) return 0;
        return VALUES.computeIfAbsent(player.getUUID(), ignored -> {
            float value = player.getPersistentData().contains(PERSISTED_STAMINA)
                    ? player.getPersistentData().getFloat(PERSISTED_STAMINA)
                    : getRules(player).maxStamina();
            return Mth.clamp(value, 0, getRules(player).maxStamina());
        });
    }

    public static void setStamina(ServerPlayer player, float value) {
        setStamina(player, value, true);
    }

    public static void setStamina(ServerPlayer player, float value, boolean resetRecoveryDelay) {
        requireEnabled();
        if (player == null || !Float.isFinite(value)) {
            throw new IllegalArgumentException("Гравець і stamina мають бути коректними.");
        }
        StaminaRules rules = getRules(player);
        float oldValue = getStamina(player);
        float clamped = Mth.clamp(value, 0, rules.maxStamina());
        VALUES.put(player.getUUID(), clamped);
        player.getPersistentData().putFloat(PERSISTED_STAMINA, clamped);
        if (resetRecoveryDelay && clamped < oldValue) {
            LAST_USE_TICK.put(player.getUUID(), player.tickCount);
        }
        sync(player);
    }

    public static void setStaminaPercent(ServerPlayer player, float percent) {
        if (!Float.isFinite(percent)) {
            throw new IllegalArgumentException("Відсоток stamina має бути скінченним числом.");
        }
        setStamina(player, getRules(player).maxStamina() * Mth.clamp(percent, 0, 1));
    }

    public static float getMaxStamina(Player player) {
        return getRules(player).maxStamina();
    }

    public static boolean isActive(Player player) {
        return enabled && player != null && getRules(player).active()
                && !(player.isCreative() || player.isSpectator());
    }

    public static boolean shouldBlockJump(Player player) {
        return isActive(player)
                && getRules(player).blockJumpWhenDepleted()
                && getStamina(player) <= 0;
    }

    public static void syncNow(ServerPlayer player) {
        requireEnabled();
        if (player == null) {
            throw new IllegalArgumentException("Гравець не може бути null.");
        }
        sync(player, true);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        RULES.remove(player.getUUID());
        VALUES.remove(player.getUUID());
        LAST_USE_TICK.remove(player.getUUID());
        LAST_SYNC.remove(player.getUUID());
        player.getPersistentData().remove(PERSISTED_STAMINA);
        ShaurmaLibNetwork.sendToPlayer(player, StaminaSyncPacket.disabled());
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        RULES.remove(uuid);
        VALUES.remove(uuid);
        LAST_USE_TICK.remove(uuid);
        LAST_SYNC.remove(uuid);
    }

    static void tick(ServerPlayer player) {
        if (!enabled) return;
        if (player.isCreative() || player.isSpectator()) {
            sync(player);
            return;
        }
        StaminaRules rules = getRules(player);
        if (!rules.active()) {
            sync(player);
            return;
        }

        float stamina = getStamina(player);
        boolean depleted = stamina <= 0;
        if (depleted) {
            player.setSprinting(false);
        }
        // Ванільний isSprinting() може стати true на короткий сплеск і
        // під час стрибка вперед (клієнт іноді виставляє прапор спринту
        // разом із forward-імпульсом стрибка, навіть без реального
        // безперервного бігу) — витрата стаміни на такий одиничний тік
        // виглядає як "стрибнув без спринту, а стаміна трохи впала".
        // Довжина руху по горизонталі — надійніший сигнал "гравець
        // справді біжить", ніж сам по собі isSprinting().
        boolean actuallyMoving = player.getDeltaMovement().horizontalDistanceSqr() > MIN_SPRINT_MOTION_SQR;
        boolean sprinting = !depleted && player.isSprinting() && actuallyMoving && player.getVehicle() == null;
        if (sprinting && rules.drainPerSecond() > 0) {
            stamina -= rules.drainPerSecond() / 20.0f;
            LAST_USE_TICK.put(player.getUUID(), player.tickCount);
            if (stamina <= 0) {
                stamina = 0;
                player.setSprinting(false);
            }
            VALUES.put(player.getUUID(), stamina);
            player.getPersistentData().putFloat(PERSISTED_STAMINA, stamina);
        } else if (rules.recoveryEnabled() && stamina < rules.maxStamina()) {
            int lastUse = LAST_USE_TICK.getOrDefault(player.getUUID(), player.tickCount);
            float delay = stamina <= 0 ? rules.emptyRecoveryDelaySeconds() : rules.recoveryDelaySeconds();
            if (player.tickCount - lastUse >= Math.round(delay * 20.0f)) {
                float recovered = Math.min(rules.maxStamina(),
                        stamina + rules.recoveryPerSecond() / 20.0f);
                VALUES.put(player.getUUID(), recovered);
                player.getPersistentData().putFloat(PERSISTED_STAMINA, recovered);
            }
        }

        if (rules.forceFullHungerWhileActive()) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0f);
        }
        sync(player);
    }

    static void sync(ServerPlayer player) {
        sync(player, false);
    }

    static void sync(ServerPlayer player, boolean force) {
        StaminaRules rules = getRules(player);
        boolean active = rules.active() && !player.isCreative() && !player.isSpectator();
        String state = active + ":" + getStamina(player) + ":" + rules.maxStamina();
        if (!force && state.equals(LAST_SYNC.get(player.getUUID()))) {
            return;
        }
        LAST_SYNC.put(player.getUUID(), state);
        ShaurmaLibNetwork.sendToPlayer(player,
                new StaminaSyncPacket(active, getStamina(player), rules.maxStamina(),
                        rules.blockJumpWhenDepleted()));
    }

    private static void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Stamina не підключена — викличте withStamina(...) на Builder.");
        }
    }

}
