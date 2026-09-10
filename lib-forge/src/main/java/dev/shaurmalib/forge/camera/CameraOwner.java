package dev.shaurmalib.forge.camera;

/**
 * Реалізується будь-яким camera-менеджером, що бере участь у
 * {@link CameraOwnershipRegistry} (KitSelect-подібна нерухома камера,
 * spectate-в-сутність, end-game реплей, SD round-intro тощо).
 */
public interface CameraOwner {

    /**
     * Викликається реєстром, коли цей власник знову стає активним
     * (верхівкою стека) після того, як власник над ним звільнив
     * камеру. Реалізація повинна відновити свою {@code mc.setCameraEntity(...)}
     * — реєстр сам камеру не встановлює, лише сигналізує, чия черга.
     */
    void resumeOwnership();
}
