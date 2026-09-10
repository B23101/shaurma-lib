package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.radio.RadioAudioDucking;
import dev.shaurmalib.forge.radio.RadioDialogOverlay;
import dev.shaurmalib.forge.radio.RadioVoiceVolumeProvider;
import net.minecraft.sounds.SoundEvent;

import java.util.function.BooleanSupplier;

/**
 * Точка вбудовування радіо-модуля (план, п. 3.33) — заміна
 * {@code RadioDialogConfig} + {@code RadioDialogManager} +
 * {@code RadioDialogPacket}/{@code RadioDialogStopPacket} +
 * {@code RadioDialogOverlay} + {@code RadioDialogSoundManager} +
 * {@code SoundCategoryDucker} snipers_shaurma одним
 * {@code withRadio(...)} на {@code ShaurmaLib.Builder}.
 * <p>
 * {@link #onAttach} лише підключає клієнтські частини (overlay-рендер
 * через {@code OverlayEngine}, і аудіо-параметри голосу диктора) —
 * серверний {@code RadioDialogManager.trigger(...)}/{@code broadcast(...)}
 * лишається статичним API, доступним завжди (не потребує attach), так
 * само як {@code TeleportService}. Завантаження {@code radio_dialogs.yml}
 * відбувається окремо через {@code RadioDialogConfigLoader.load(...)},
 * типово підписане на {@code ConfigReloadBus} консюмера так само, як
 * решта конфіг-класів (loot/kits/items).
 */
public interface RadioModuleHook {
    void onAttach(FMLModuleContext ctx);

    /**
     * @param volumeProvider  постачальник гучності голосу диктора (0..1),
     *                        типово прив'язаний до власного слайдера
     *                        консюмера (напр. {@code ModSoundCategory.ANNOUNCER});
     *                        {@code null} — завжди повна гучність.
     * @param startSound      звук "клац" на старті репліки (jar-ресурс
     *                        консюмера), {@code null} — пропустити крок.
     * @param noiseSound      циклічний фоновий шум рації на час голосу,
     *                        {@code null} — без фонового шуму.
     * @param endSound        звук завершення репліки, {@code null} — пропустити.
     * @param overlayVisible  умова показу overlay (опції відео, death-камера
     *                        консюмера тощо); {@code () -> true}, якщо немає таких умов.
     */
    static RadioModuleHook of(RadioVoiceVolumeProvider volumeProvider,
                               SoundEvent startSound, SoundEvent noiseSound, SoundEvent endSound,
                               BooleanSupplier overlayVisible) {
        return ctx -> {
            RadioAudioDucking.configure(volumeProvider, startSound, noiseSound, endSound);
            RadioDialogOverlay.attach(overlayVisible);
        };
    }
}
