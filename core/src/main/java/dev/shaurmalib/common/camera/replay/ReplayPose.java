package dev.shaurmalib.common.camera.replay;

/** Поза камери в конкретний момент клипу. {@code fov <= 0} — без перевизначення. */
public record ReplayPose(double x, double y, double z, float yaw, float pitch, float fov) {}
