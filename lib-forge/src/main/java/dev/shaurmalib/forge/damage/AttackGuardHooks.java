package dev.shaurmalib.forge.damage;

import dev.shaurmalib.common.damage.DamageContext;
import dev.shaurmalib.common.damage.DamageInterceptorRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Другий, РАНІШИЙ рівень захисту від урону — {@link LivingAttackEvent},
 * а не тільки {@link net.minecraftforge.event.entity.living.LivingHurtEvent}
 * (див. {@link DamageGuardHooks}).
 * <p>
 * <b>Чому цього не було і чому це важливо.</b> Оригінальний
 * {@code snipers_shaurma} мав ДВА окремі обробники, що незалежно
 * блокували урон гравцю:
 * {@code AttackPreventionHandler} на {@code LivingAttackEvent} з
 * {@link EventPriority#HIGHEST} — і лише {@code PlayerDamageEventHandler}
 * на {@code LivingHurtEvent} з {@link EventPriority#HIGH}. Перенесення в
 * бібліотеку (план, п. 3.26) зробило лише другий шлях
 * ({@code DamageGuardHooks}) — {@code LivingAttackEvent} узагальнений
 * не був, тому будь-яка причина блокування, зареєстрована в
 * {@link DamageInterceptorRegistry}, спрацьовувала лише на
 * {@code LivingHurtEvent}. {@code LivingAttackEvent} стріляє РАНІШЕ:
 * саме на неї Forge/vanilla спираються, щоб взагалі вирішити, чи буде
 * ЗАВДАНО урону (частина шляхів удару — включно з деякими сторонніми
 * мод-інтеграціями зброї, що самі перевіряють {@code isCanceled()} цієї
 * події перед власним розрахунком шкоди, а не лише {@code LivingHurtEvent}
 * — перевіряють саме тут; без цього хука такий шлях міг пробити захист,
 * навіть коли причина в реєстрі коректно повертає {@code true}).
 * <p>
 * Той самий {@link DamageInterceptorRegistry}, той самий принцип "OR
 * по всіх причинах" — жодної нової причини консюмер не реєструє;
 * причина, зареєстрована для {@link DamageGuardHooks}, автоматично
 * покриває і цей, ранішій, шлях.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class AttackGuardHooks {

    private AttackGuardHooks() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        // amount тут не має точного сенсу (LivingAttackEvent стріляє до
        // розрахунку броні/резистенсу), але DamageContext вимагає float —
        // передаємо -1 як явний сигнал "невідомо/до розрахунку", жодна
        // наявна причина блокування (усі перевіряють лише фазу/стан
        // гравця, не amount) від цього не залежить.
        DamageContext ctx = new DamageContext(
                DamageContext.SourceKind.VANILLA,
                event.getSource().type().msgId(),
                -1f,
                event.getSource().getDirectEntity() != null
                        && event.getSource().getDirectEntity() != event.getSource().getEntity());

        if (DamageInterceptorRegistry.isBlocked(victim, event.getSource(), -1f, ctx)) {
            event.setCanceled(true);
        }
    }
}
