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

    /**
     * Вмикає фри-камеру на заданій позиції. Якщо вже активна — спершу
     * деспаунить стару entity й тихо знімає старий запис зі стека
     * ({@link CameraOwnershipRegistry#discardSilently}, БЕЗ виклику
     * resumeOwnership наступного власника чи падіння на гравця) — щоб
     * не було видимого проміжного кадру між старою і новою сесією цього
     * контролера (джерело нюансу: {@code SDRoundCameraManager.startIdle()}
     * викликає {@code stopInternal(false)} саме з цією метою при рестарті
     * intro-камери нового раунду поки попередня ще не DONE). Використання
     * звичайного {@link CameraOwnershipRegistry#release} тут замість
     * {@code discardSilently} спричиняло б саме такий небажаний стрибок.
     */
    public void enable(float x, float y, float z, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (token != null) {
            if (freeCamera != null) {
                freeCamera.despawn();
                freeCamera = null;
            }
            CameraOwnershipRegistry.discardSilently(token);
            token = null;
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

    /**
     * ВАЖЛИВО (антистрибок фікс, збережений з оригінального
     * {@code KitSelectCameraManager.disableInternal}): {@link CameraOwnershipRegistry#release}
     * ВИКЛИКАЄТЬСЯ ПЕРШИМ — воно само перемикає {@code mc.setCameraEntity(...)}
     * на наступного власника/гравця (через {@code resumeTopOrPlayer()}/
     * {@code resumeOwnership()}) — і лише ПІСЛЯ цього деспаунимо
     * {@link #freeCamera}. Якщо деспаунити першим, між {@code discard()} і
     * подальшим {@code setCameraEntity()} є мікрофрейм, де Minecraft сам
     * автоматично перемикає камеру на гравця (бачачи видалену camera
     * entity) — і коли {@code release()} відпрацьовує вже ПІСЛЯ цього, він
     * або дублює той самий {@code setCameraEntity(player)} (непомітно), або,
     * якщо стек не порожній, ще й одразу перебиває щойно застосований
     * ванільний авто-скид новим {@code setCameraEntity(nextOwner)} — тобто
     * гравець встигає побачити один кадр на власному тілі перед тим, як
     * камера "стрибне" до наступного власника. Порядок нижче усуває обидва
     * випадки: камера вже стоїть на правильній цілі до того, як стара
     * FreeCameraEntity взагалі видаляється зі світу.
     */
    private void disableInternal(boolean releaseOwnership) {
        if (releaseOwnership && token != null) {
            CameraOwnershipRegistry.release(token);
            token = null;
        }
        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera = null;
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
