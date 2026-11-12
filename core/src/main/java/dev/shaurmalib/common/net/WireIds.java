package dev.shaurmalib.common.net;

/**
 * Таблиця id пакетів на дроті — 1:1 з порядком {@code ShaurmaLibNetwork.register()} у legacy
 * (PROTOCOL_VERSION "2"). Незмінна: клієнт старої збірки має розуміти сервер нової.
 * Пакети, ще не перенесені в core, зберігають тут свої зарезервовані слоти.
 */
public final class WireIds {
    private WireIds() {}
    public static final int ITEM_ANIM = 0, CHAT_SEND = 1, CHAT_BROADCAST = 2, RADIO_DIALOG = 3, RADIO_DIALOG_STOP = 4,
            GRAFFITI_SYNC = 5, GRAFFITI_REMOVE = 6, GRAFFITI_IMAGE_START = 7, GRAFFITI_IMAGE_CHUNK = 8,
            GRAFFITI_REQUEST_IMAGE = 9, PLAY_SOUND = 10, STOP_SOUND = 11, INVENTORY_SLOT_ALLOCATION = 12,
            STAMINA_SYNC = 13, CHAT_CHANNELS_SYNC = 14, REPLAY_PLAY = 15, REPLAY_STOP = 16,
            FREE_CAMERA_CHUNK_REQUEST = 17;
}
