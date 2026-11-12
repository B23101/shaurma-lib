package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.stamina.StaminaClientHooks;
import dev.shaurmalib.forge.stamina.StaminaService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Блокування спринту за станом stamina — замість того, щоб сервер скидав
 * прапорець, а клієнт одразу вмикав його знову.
 * <p>
 * {@code LivingEntity#setSprinting} викликають і клієнтський
 * {@code LocalPlayer#aiStep} (клавіша спринту / подвійне W), і сервер
 * (пакет {@code START_SPRINTING}), тож один міксин закриває обидві
 * сторони: коли спринт заборонений, {@code true} підміняється на
 * {@code false}. {@code false} завжди проходить без змін — вимкнути
 * спринт можна будь-коли.
 * <ul>
 *   <li>сервер: {@link StaminaService#shouldBlockSprint};</li>
 *   <li>клієнт: прапорець, який сервер надсилає в {@code StaminaSyncPacket}
 *       (лише для локального гравця — чужі моделі не чіпаємо).</li>
 * </ul>
 * Голод тут не використовується взагалі.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityStaminaSprint {
    @ModifyVariable(method = "setSprinting(Z)V", at = @At("HEAD"), argsOnly = true)
    private boolean shaurma$blockSprintWhenStaminaDepleted(boolean sprinting) {
        if (!sprinting) {
            return false;
        }
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player)) {
            return true;
        }
        boolean blocked = player.level().isClientSide
                ? StaminaClientHooks.shouldBlockSprint(player)
                : StaminaService.shouldBlockSprint(player);
        return !blocked;
    }
}
