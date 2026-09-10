package dev.shaurmalib.common.damage;

/**
 * Контекст однієї спроби нанесення урону гравцю (план, п. 3.26) —
 * передається кожному {@link DamageInterceptor} перед фактичним
 * застосуванням урону. Чиста дата-структура без Minecraft/Forge типів
 * (жертва/джерело урону лишаються в {@code lib-forge}-обгортці, дивись
 * {@code DamageGuardHooks}), щоб саму умову блокування (наприклад
 * "чи активний щит саппера прямо зараз") можна було описати й
 * протестувати без піднятого сервера.
 * <p>
 * {@link #sourceKind()} розрізняє шлях, яким прийшов урон — це
 * важливо, бо частина джерел (звичайний {@code LivingHurtEvent}) і
 * частина (стороння зброя на кшталт TACZ через
 * {@code EntityHurtByGunEvent}) НЕ проходять через один і той самий
 * ванільний шлях, тому одна причина блокування (наприклад
 * "недоторканість під час kit-select") має спрацьовувати однаково
 * для обох — дивись {@link GunDamageBridge}.
 */
public final class DamageContext {

    /** Джерело урону — розрізняє ванільний шлях і сторонні "обхідні" шляхи типу TACZ-зброї. */
    public enum SourceKind {
        /** Стандартний {@code LivingHurtEvent} шлях (ванільний урон, миксинова зброя тощо). */
        VANILLA,
        /** Урон від кулі стороннього мода зброї (TACZ {@code EntityHurtByGunEvent} і подібні). */
        GUN,
        /** Довільне інше джерело, яке консюмер прокидає вручну через {@link GunDamageBridge}-подібний міст. */
        CUSTOM
    }

    private final SourceKind sourceKind;
    private final String damageTypeId;
    private final float amount;
    private final boolean isProjectile;

    public DamageContext(SourceKind sourceKind, String damageTypeId, float amount, boolean isProjectile) {
        this.sourceKind = sourceKind;
        this.damageTypeId = damageTypeId;
        this.amount = amount;
        this.isProjectile = isProjectile;
    }

    public SourceKind sourceKind() {
        return sourceKind;
    }

    /** Ідентифікатор типу урону (наприклад {@code "minecraft:mob_attack"}, {@code "tacz:gun"}). */
    public String damageTypeId() {
        return damageTypeId;
    }

    public float amount() {
        return amount;
    }

    public boolean isProjectile() {
        return isProjectile;
    }
}
