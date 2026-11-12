package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.core.util.Ease;
import dev.shaurmalib.common.playeranim.PoseEase;

/**
 * {@link PoseEase} → PlayerAnimator {@link Ease} через числовий id.
 * <p>
 * {@code Ease.getEase(byte)} при невідомому id мовчки повертає {@code LINEAR},
 * тому розсинхрон id був би непомітним. Від цього страхує
 * {@code PoseEaseParityTest}: для кожного {@code PoseEase} перевіряє, що
 * {@code Ease.getEase(palId).name().equals(name())}.
 */
final class PoseEaseMapper {

    private PoseEaseMapper() {}

    static Ease toPal(PoseEase ease) {
        return Ease.getEase((ease == null ? PoseEase.DEFAULT : ease).palId());
    }
}
