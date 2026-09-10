package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.camera.CameraOwnershipRegistry;

/**
 * Точка вбудовування пакета "вільна камера" (план, п. 3.15) — заміна
 * {@code FreeCamera}/{@code KitSelectCameraManager}/
 * {@code CameraSpectateHandler}/{@code EndGameCameraManager}/
 * {@code SDRoundCameraManager} snipers_shaurma одним координованим
 * пакетом через {@code ShaurmaLib.Builder.withFreeCamera()} на
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder}.
 * <p>
 * {@link #onAttach} нічого не реєструє сам — {@link CameraOwnershipRegistry}
 * і готові контролери ({@code StationaryCameraController}/
 * {@code EntitySpectateController}/{@code CinematicPathController}/
 * {@code FreeFlyCameraController}) — статичні сервіси без стану
 * ініціалізації, як і решта хуків бібліотеки ({@code TeleportModuleHook},
 * {@code ScreenEffectModuleHook}) — метод лише документує свідоме
 * підключення, як і решта.
 */
public interface FreecamModuleHook {
    void onAttach(FMLModuleContext ctx);
}
