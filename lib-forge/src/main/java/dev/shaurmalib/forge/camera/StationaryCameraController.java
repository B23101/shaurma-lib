package dev.shaurmalib.forge.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Вільна камера на фіксованій нерухомій позиції — узагальнення
 * {@code KitSelectCameraManager} (оригінал: спавнить {@link FreeCameraEntity}
 * на заданій точці, передає їй керування, повертає гравцю при {@link #disable}).
 * <p>
 * На відміну від оригіналу, координація з іншими camera-системами
 * (EndGame-кінематика, spectate-в-сутність) відбувається через
 * {@link CameraOwnershipRegistry} — цей клас нічого не знає про
 * конкретні інші менеджери камери (оригінал жорстко перевіряв
 * {@code EndGameCameraManager.isActive()} і
 * {@code CameraSpectateHandler.getSpectatingEntityId()} напряму).
 * <p>
 * Кожен консюмер (snipers KIT_SELECT, майбутній maniac-режим з власною
 * стаціонарною камерою) створює власний екземпляр — стан не статичний,
 * на відміну від оригінального {@code KitSelectCameraManager} (щоб два
 * незалежних використання стаціонарної камери в одному моді не
 * ділили один спільний стан).
 */
@OnlyIn(Dist.CLIENT)
public final class StationaryCameraController implements CameraOwner {

    private FreeCameraEntity freeCamera;
    private CameraOwnershipRegistry.OwnershipToken token;

    /** Вмикає фри-камеру на заданій позиції. Якщо вже активна — спершу вимикає стару. */
    public void enable(float x, float y, float z, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (token != null) {
            disableInternal(false);
        }

        ClientLevel level = (ClientLevel) mc.level;
        freeCamera = new FreeCameraEntity(level, x, y, z, yaw, pitch);
        freeCamera.spawn();
        mc.setCameraEntity(freeCamera);
        token = CameraOwnershipRegistry.acquire(this);
    }

    /** Пересуває вже активну камеру на нову позицію без повторного спавну (для дрейфу/ручного апдейту позиції). */
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        if (freeCamera != null) {
            freeCamera.moveSmooth(x, y, z, yaw, pitch);
        }
    }

    /** Вимикає фри-камеру і звільняє власність — {@link CameraOwnershipRegistry} сам вирішує, кому передати камеру далі. */
    public void disable() {
        disableInternal(true);
    }

    private void disableInternal(boolean releaseOwnership) {
        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera = null;
        }
        if (releaseOwnership && token != null) {
            CameraOwnershipRegistry.release(token);
            token = null;
        }
    }

    public boolean isActive() {
        return token != null;
    }

    public FreeCameraEntity freeCamera() {
        return freeCamera;
    }

    /**
     * {@link CameraOwnershipRegistry} викликає це, коли ця камера знову
     * стає активним власником (верхівкою стека) після того, як власник
     * над нею звільнив камеру — типово: NPC-spectate завершився поки
     * KIT_SELECT ще тривав. Відновлюємо {@code mc.setCameraEntity(...)}
     * на власну {@link FreeCameraEntity}, якщо вона ще існує.
     */
    @Override
    public void resumeOwnership() {
        Minecraft mc = Minecraft.getInstance();
        if (freeCamera != null && mc.getCameraEntity() != freeCamera) {
            mc.setCameraEntity(freeCamera);
        }
    }
}
