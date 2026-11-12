package dev.shaurmalib.forge.hunger;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Керування голодом гравця поза ванільною механікою (план, п. "голод у
 * лобі"). Три режими — {@link FoodMode#VANILLA}, {@link FoodMode#ALWAYS_FULL},
 * {@link FoodMode#FIXED} — див. опис там.
 * <p>
 * Бібліотека сама не знає, що таке "лобі": режим для гравця виставляє
 * споживач (головний мод) у потрібний момент через {@link #setMode}.
 * Підключається один раз через {@code ShaurmaLib.Builder#withFoodControl()}.
 */
public final class FoodControlService {

    private static final Map<UUID, FoodMode> MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> FIXED_LEVEL = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> FIXED_SATURATION = new ConcurrentHashMap<>();
    private static volatile boolean enabled;

    private FoodControlService() {}

    public static void enable() {
        enabled = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Перемикає режим голоду для гравця. При переході в {@link FoodMode#FIXED}
     * без попереднього виклику {@link #setFixedFood} фіксується поточне
     * значення голоду гравця на момент перемикання.
     */
    public static void setMode(ServerPlayer player, FoodMode mode) {
        requireEnabled();
        if (player == null || mode == null) {
            throw new IllegalArgumentException("Гравець і режим голоду не можуть бути null.");
        }
        MODES.put(player.getUUID(), mode);
        if (mode == FoodMode.FIXED && !FIXED_LEVEL.containsKey(player.getUUID())) {
            FoodData food = player.getFoodData();
            FIXED_LEVEL.put(player.getUUID(), food.getFoodLevel());
            FIXED_SATURATION.put(player.getUUID(), food.getSaturationLevel());
        }
        if (mode != FoodMode.FIXED) {
            FIXED_LEVEL.remove(player.getUUID());
            FIXED_SATURATION.remove(player.getUUID());
        }
        applyNow(player);
    }

    public static FoodMode getMode(Player player) {
        if (player == null) return FoodMode.VANILLA;
        return MODES.getOrDefault(player.getUUID(), FoodMode.VANILLA);
    }

    /**
     * "Становлення" зафіксованого значення для режиму {@link FoodMode#FIXED} —
     * єдиний спосіб змінити голод, поки цей режим активний. Значення так і
     * тримається (ванільна витрата на нього не впливає), поки його знову не
     * змінять цим методом або не перемкнуть режим.
     */
    public static void setFixedFood(ServerPlayer player, int foodLevel, float saturation) {
        requireEnabled();
        if (player == null) {
            throw new IllegalArgumentException("Гравець не може бути null.");
        }
        if (!Float.isFinite(saturation)) {
            throw new IllegalArgumentException("Saturation має бути скінченним числом.");
        }
        int clampedLevel = Mth.clamp(foodLevel, 0, 20);
        float clampedSaturation = Mth.clamp(saturation, 0f, clampedLevel);
        FIXED_LEVEL.put(player.getUUID(), clampedLevel);
        FIXED_SATURATION.put(player.getUUID(), clampedSaturation);
        if (getMode(player) == FoodMode.FIXED) {
            applyNow(player);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        MODES.remove(uuid);
        FIXED_LEVEL.remove(uuid);
        FIXED_SATURATION.remove(uuid);
    }

    /** Викликається щотіку (після ванільного {@code FoodData.tick()}) з {@link FoodControlHooks}. */
    static void tick(ServerPlayer player) {
        if (!enabled) return;
        FoodMode mode = getMode(player);
        if (mode == FoodMode.VANILLA) return;
        applyNow(player);
    }

    private static void applyNow(ServerPlayer player) {
        FoodMode mode = getMode(player);
        FoodData food = player.getFoodData();
        switch (mode) {
            case ALWAYS_FULL -> {
                if (food.getFoodLevel() != 20 || food.getSaturationLevel() != 20f) {
                    food.setFoodLevel(20);
                    food.setSaturation(20f);
                }
            }
            case FIXED -> {
                int level = FIXED_LEVEL.getOrDefault(player.getUUID(), 20);
                float saturation = FIXED_SATURATION.getOrDefault(player.getUUID(), 20f);
                if (food.getFoodLevel() != level || food.getSaturationLevel() != saturation) {
                    food.setFoodLevel(level);
                    food.setSaturation(saturation);
                }
            }
            case VANILLA -> { /* нічого не робимо */ }
        }
    }

    private static void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException(
                    "FoodControl не підключений — викличте withFoodControl() на Builder.");
        }
    }
}
