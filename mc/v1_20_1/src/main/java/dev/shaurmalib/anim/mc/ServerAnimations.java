package dev.shaurmalib.anim.mc;

import dev.shaurmalib.anim.net.AnimMessage;
import dev.shaurmalib.anim.net.AnimNetwork;
import dev.shaurmalib.anim.runtime.Slot;
import net.minecraft.world.entity.Entity;

/**
 * Серверний API: запустити анімацію так, щоб її побачили ВСІ, хто бачить сутність (і вона сама).
 * Сервер нічого не семплить — лише шле id кліпа; кліпи живуть у ресурсах клієнта.
 *
 * <pre>
 * ServerAnimations.play(player, "maniacmod:medkit_use");
 * ServerAnimations.play(player, AnimMessage.PlayRequestSpec.of("maniacmod:scissors_cut").slot(Slot.ACTION));
 * </pre>
 */
public final class ServerAnimations {

    private ServerAnimations() {}

    public static void play(Entity entity, String clipId) {
        play(entity, AnimMessage.PlayRequestSpec.of(clipId).restart(true));
    }

    public static void play(Entity entity, AnimMessage.PlayRequestSpec spec) {
        AnimNetwork.get().sendToTracking(entity, AnimMessage.play(entity.getUUID(), spec), true);
    }

    public static void stop(Entity entity, String clipId) {
        AnimNetwork.get().sendToTracking(entity, AnimMessage.stopClip(entity.getUUID(), clipId, Float.NaN), true);
    }

    public static void stop(Entity entity, Slot slot) {
        AnimNetwork.get().sendToTracking(entity, AnimMessage.stopSlot(entity.getUUID(), slot, Float.NaN), true);
    }

    public static void stopAll(Entity entity) {
        AnimNetwork.get().sendToTracking(entity, AnimMessage.stopAll(entity.getUUID(), 0.2f), true);
    }
}
