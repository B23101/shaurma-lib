package dev.shaurmalib.forge.damage;

import dev.shaurmalib.common.damage.DamageContext;
import dev.shaurmalib.common.damage.DamageInterceptorRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Статичний міст для урону від стороннього мода зброї, що обходить
 * ванільний {@code LivingHurtEvent} (план, п. 3.26 — "включно з окремим
 * шляхом для стороннього джерела урону типу TACZ"). У самому оригіналі
 * snipers_shaurma TACZ надсилає власну {@code EntityHurtByGunEvent}, яка
 * НЕ є підтипом {@code LivingHurtEvent} — без окремого мосту зареєстрований
 * {@link dev.shaurmalib.common.damage.DamageInterceptor} (наприклад щит
 * саппера чи недоторканість kit-select) працював би лише для ванільної
 * зброї, а кулі TACZ пробивали б захист.
 * <p>
 * {@code lib-forge} НЕ залежить від TACZ напряму (жоден
 * {@code compileOnly}/{@code implementation} на TACZ у {@code build.gradle}
 * — план, розділ 4, п. 3: бібліотека не повинна форсувати сторонній
 * зброярський мод для консюмерів на кшталт майбутнього {@code maniac-mode},
 * де TACZ може взагалі бути відсутній). Тому міст не підписується на
 * {@code EntityHurtByGunEvent} сам — консюмер, у якого TACZ присутній
 * (сам snipers_shaurma), викликає {@link #tryBlock} зі свого власного
 * TACZ-специфічного {@code @SubscribeEvent}-хука, передаючи вже готові
 * ServerPlayer/DamageSource/amount; сам {@code EntityHurtByGunEvent}-тип
 * лишається невідомим цій бібліотеці.
 */
public final class GunDamageBridge {

    private GunDamageBridge() {}

    /**
     * Викликається консюмером зі свого TACZ-специфічного event-хука.
     *
     * @return {@code true}, якщо урон має бути скасований (консюмер сам
     * викликає {@code event.setCanceled(true)}/еквівалент TACZ API —
     * бібліотека не знає конкретного класу події TACZ, тому не може
     * скасувати її сама).
     */
    public static boolean tryBlock(ServerPlayer victim, DamageSource source, float amount, boolean isProjectile) {
        DamageContext ctx = new DamageContext(DamageContext.SourceKind.GUN, "tacz:gun", amount, isProjectile);
        return DamageInterceptorRegistry.isBlocked(victim, source, amount, ctx);
    }

    /**
     * Перевантаження для довільного іншого стороннього джерела урону, що
     * теж не проходить через {@code LivingHurtEvent} (план, {@link
     * DamageContext.SourceKind#CUSTOM}) — той самий принцип, просто без
     * TACZ-специфічної семантики "gun"/"projectile".
     */
    public static boolean tryBlockCustom(ServerPlayer victim, DamageSource source, float amount, String damageTypeId) {
        DamageContext ctx = new DamageContext(DamageContext.SourceKind.CUSTOM, damageTypeId, amount, false);
        return DamageInterceptorRegistry.isBlocked(victim, source, amount, ctx);
    }
}
