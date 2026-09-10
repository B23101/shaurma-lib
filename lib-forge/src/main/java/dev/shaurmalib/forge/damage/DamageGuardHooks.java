package dev.shaurmalib.forge.damage;

import dev.shaurmalib.common.damage.DamageContext;
import dev.shaurmalib.common.damage.DamageInterceptorRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Форг-хук {@link DamageInterceptorRegistry} для стандартного ванільного
 * шляху урону (план, п. 3.26). Перенесено з оригінального підходу
 * snipers_shaurma — підписка на {@link LivingHurtEvent} з
 * {@link EventPriority#HIGH}, а не покладання на
 * {@code Entity.isInvulnerableTo} (який консюмер не завжди контролює,
 * наприклад коли урон приходить з іншого мода, що не перевіряє
 * ванільний invulnerability-прапорець узагалі).
 * <p>
 * {@link EventPriority#HIGH} — свідомий вибір: інтерцептор має
 * спрацювати РАНІШЕ, ніж інші листенери (наприклад ті, що рахують і
 * розсилають kill-feed чи HUD-урону) встигнуть відреагувати на подію,
 * яку зрештою буде скасовано.
 * <p>
 * Урон від сторонніх джерел, що НЕ проходить через
 * {@link LivingHurtEvent} (TACZ {@code EntityHurtByGunEvent} і подібні)
 * — окремий шлях, дивись {@link GunDamageBridge}; обидва шляхи
 * звіряються з тим самим {@link DamageInterceptorRegistry}, тому одна
 * зареєстрована причина блокування працює для обох джерел без
 * дублювання логіки.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class DamageGuardHooks {

    private DamageGuardHooks() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        DamageContext ctx = new DamageContext(
                DamageContext.SourceKind.VANILLA,
                event.getSource().type().msgId(),
                event.getAmount(),
                event.getSource().getDirectEntity() != null
                        && event.getSource().getDirectEntity() != event.getSource().getEntity());

        if (DamageInterceptorRegistry.isBlocked(victim, event.getSource(), event.getAmount(), ctx)) {
            event.setCanceled(true);
        }
    }
}
