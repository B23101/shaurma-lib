package dev.shaurmalib.forge.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Вселяє клієнтську камеру в задану ЖИВУ сутність (напр. NPC-персонаж
 * у літаку) — узагальнення {@code CameraSpectateHandler}. Опційно
 * приховує рендер власного тіла гравця на час спостереження.
 * <p>
 * На відміну від оригіналу, координація з іншими camera-системами йде
 * через {@link CameraOwnershipRegistry} — не через жорстко прописані
 * перевірки {@code KitSelectCameraManager.isActive()}/
 * {@code DropAnimationHandler.isActive()}/{@code EndGameCameraManager.isActive()}.
 * Кожен консюмер реєструє один екземпляр цього класу на event bus
 * (клас не статичний, на відміну від оригіналу — щоб два незалежних
 * spectate-використання не ділили спільний стан).
 */
@OnlyIn(Dist.CLIENT)
public final class EntitySpectateController implements CameraOwner {

    private boolean hideOwnBody = false;
    private int spectatingEntityId = -1;
    private CameraOwnershipRegistry.OwnershipToken token;

    /** Починає спостереження за сутністю з даним ID; опційно ховає власне тіло гравця. */
    public void startSpectating(int entityId, boolean hideOwnBody) {
        this.spectatingEntityId = entityId;
        this.hideOwnBody = hideOwnBody;
        if (token == null) {
            token = CameraOwnershipRegistry.acquire(this);
        }
    }

    /** Зупиняє спостереження і звільняє власність над камерою. */
    public void stopSpectating() {
        spectatingEntityId = -1;
        hideOwnBody = false;
        if (token != null) {
            CameraOwnershipRegistry.release(token);
            token = null;
        }
    }

    public int getSpectatingEntityId() {
        return spectatingEntityId;
    }

    public boolean isActive() {
        return spectatingEntityId != -1;
    }

    public boolean isHidingOwnBody() {
        return hideOwnBody;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (spectatingEntityId == -1) return;
        // Тікаємо лише поки ми — активний власник камери; якщо нас
        // тимчасово витіснено (напр. EndGame-кінематика зверху в стеку),
        // реєстр сам не дасть нам самовільно перехопити камеру назад.
        if (!CameraOwnershipRegistry.isActiveOwner(this)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(spectatingEntityId);
        if (entity != null && mc.getCameraEntity() != entity) {
            mc.setCameraEntity(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!hideOwnBody) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Player player = event.getEntity();
        if (player.getUUID().equals(mc.player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * {@link CameraOwnershipRegistry} викликає це, коли spectate знову
     * стає активним власником після того, як власник над ним звільнив
     * камеру. Відновлює {@code mc.setCameraEntity(...)} на ціль
     * спостереження, якщо вона все ще існує в світі.
     */
    @Override
    public void resumeOwnership() {
        if (spectatingEntityId == -1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(spectatingEntityId);
        if (entity != null) {
            mc.setCameraEntity(entity);
        }
    }
}
