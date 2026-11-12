package dev.shaurmalib.forge.module;

/**
 * Точка вбудовування власного блокування урону (план, п. 3.26) — заміна
 * {@code PlayerDamageEventHandler}, {@code AttackPreventionHandler},
 * {@code PigProtectionHandler}, {@code SapperPassiveHandler},
 * {@code SCNDomeShieldGuard}, {@code BodyDamageEventHandler}
 * snipers_shaurma одним {@code withDamageGuard()} на
 * {@code ShaurmaLib.Builder}.
 * <p>
 * {@link #onAttach} нічого не реєструє сам — {@code
 * dev.shaurmalib.forge.damage.DamageGuardHooks} (ванільний
 * {@code LivingHurtEvent} шлях) завжди підписаний на bus, як і решта
 * "завжди присутніх, але бездіяльних без стану" хуків бібліотеки
 * ({@code InteractionLockHooks}, {@code ItemAnimationEngine}) — нуль
 * накладних витрат, доки жоден {@code DamageInterceptor} не
 * зареєстровано. Метод лише документує свідоме підключення консюмером,
 * як і решта {@code ModuleHook}-маркерів.
 * <p>
 * Реєстрація конкретних причин блокування —
 * {@code DamageInterceptorRegistry.register(id, interceptor)} —
 * лишається на боці консюмера (кожна причина специфічна до продуктової
 * логіки конкретного режиму: щит саппера, недоторканість kit-select
 * тощо), так само як {@code ItemDefinitionRegistry.register(...)} для
 * animated items. Сторонні джерела урону, що обходять
 * {@code LivingHurtEvent} (TACZ і подібні) — дивись
 * {@code dev.shaurmalib.forge.damage.GunDamageBridge}, окремий шлях,
 * що звіряється з тим самим реєстром без потреби в додатковому
 * підключенні через цей hook.
 */
public interface DamageModuleHook {
    void onAttach(FMLModuleContext ctx);

    static DamageModuleHook of() {
        return ctx -> { /* DamageGuardHooks завжди підписаний — нічого додатково реєструвати не потрібно. */ };
    }
}
