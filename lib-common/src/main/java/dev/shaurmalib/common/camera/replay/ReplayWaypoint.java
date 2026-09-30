package dev.shaurmalib.common.camera.replay;

/**
 * Позиція камери у момент {@code atMs} від початку скрипта. Використовується
 * сервером, щоб заздалегідь знати, ЯКІ чанки знадобляться клієнту і КОЛИ
 * (див. {@code FreeCameraChunkService} у lib-forge).
 */
public record ReplayWaypoint(long atMs, double x, double y, double z) {}
