package dev.shaurmalib.common.offline;

import dev.shaurmalib.common.damage.DamageContext;
import dev.shaurmalib.common.damage.DamageInterceptorRegistry;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Objects;

/**
 * Головна точка гнучкості для урону по тілу (план, §4.9). Викликається на
 * сервері; режим сам застосовує урон до своєї моделі HP і повертає результат.
 */
@FunctionalInterface
public interface OfflineDamagePolicy {

    OfflineHitResult onHit(OfflineRecord record, DamageSource source, float amount, DamageContext ctx);

    /**
     * Власна модель HP режиму (у maniac це {@code context.damage(uuid, ...)}).
     */
    @FunctionalInterface
    interface HpModel {
        /** @return {@code true}, якщо після цього удару власник «мертвий». */
        boolean damage(OfflineRecord record, DamageSource source, float amount);
    }

    /**
     * Паритет із гравцем: удар проганяється через ті самі правила блокування
     * ({@link DamageInterceptorRegistry#registerOwnerRule}), що й для онлайн-гравця,
     * а далі віднімається HP за моделлю режиму. Окремого «шляху для офлайну» в
     * режимі писати не треба.
     */
    static OfflineDamagePolicy mirrorPlayerRules(HpModel hp) {
        Objects.requireNonNull(hp, "hp");
        return (record, source, amount, ctx) -> {
            if (DamageInterceptorRegistry.isBlockedForOwner(record.owner(), source, amount, ctx)) {
                return OfflineHitResult.IGNORED;
            }
            return hp.damage(record, source, amount) ? OfflineHitResult.KILLED : OfflineHitResult.HIT;
        };
    }

    /**
     * Ванільне HP тіла ({@link OfflineSpec#initialHealth()}) — для режимів без
     * власної моделі HP. Правила блокування не застосовуються.
     */
    static OfflineDamagePolicy vanillaLike() {
        return (record, source, amount, ctx) ->
                record.applyDamage(amount) <= 0.0f ? OfflineHitResult.KILLED : OfflineHitResult.HIT;
    }
}
