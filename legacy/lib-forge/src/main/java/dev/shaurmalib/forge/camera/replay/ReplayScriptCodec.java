package dev.shaurmalib.forge.camera.replay;

import dev.shaurmalib.common.camera.replay.ReplayClip;
import dev.shaurmalib.common.camera.replay.ReplayInterpolation;
import dev.shaurmalib.common.camera.replay.ReplayPoint;
import dev.shaurmalib.common.camera.replay.ReplayScript;
import dev.shaurmalib.common.camera.replay.ReplayTransition;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Запис/читання {@link ReplayScript} у мережевий буфер. Формат стабільний: нові поля — лише в кінець + bump версії каналу. */
public final class ReplayScriptCodec {

    /** Захист від битого пакета: жодних гігантських списків. */
    private static final int MAX_CLIPS = 256;
    private static final int MAX_POINTS = 4096;

    private ReplayScriptCodec() {}

    public static void write(FriendlyByteBuf buf, ReplayScript script) {
        buf.writeVarLong(script.totalCutMs());
        buf.writeVarInt(script.clips().size());
        for (ReplayClip clip : script.clips()) {
            buf.writeUtf(clip.id(), 64);
            buf.writeEnum(clip.interpolation());
            buf.writeEnum(clip.transition());
            buf.writeVarInt(clip.dipMs());
            buf.writeVarInt(clip.sharedIntervalMs());
            buf.writeVarInt(clip.cutAtMs());
            buf.writeFloat(clip.shake());
            buf.writeUtf(clip.audience(), 32);
            buf.writeVarInt(clip.points().size());
            for (ReplayPoint p : clip.points()) {
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeFloat(p.yaw());
                buf.writeFloat(p.pitch());
                buf.writeFloat(p.fov());
                buf.writeVarInt(p.intervalToNextMs());
            }
        }
    }

    public static ReplayScript read(FriendlyByteBuf buf) {
        long totalCut = buf.readVarLong();
        int clipCount = Math.min(buf.readVarInt(), MAX_CLIPS);
        List<ReplayClip> clips = new ArrayList<>();
        for (int c = 0; c < clipCount; c++) {
            String id = buf.readUtf(64);
            ReplayInterpolation interpolation = buf.readEnum(ReplayInterpolation.class);
            ReplayTransition transition = buf.readEnum(ReplayTransition.class);
            int dip = buf.readVarInt();
            int shared = buf.readVarInt();
            int cutAt = buf.readVarInt();
            float shake = buf.readFloat();
            String audience = buf.readUtf(32);
            int pointCount = Math.min(buf.readVarInt(), MAX_POINTS);
            ReplayClip.Builder b = ReplayClip.builder(id)
                .interpolation(interpolation)
                .transition(transition, dip)
                .sharedIntervalMs(shared)
                .cutAtMs(cutAt)
                .shake(shake)
                .audience(audience);
            for (int i = 0; i < pointCount; i++) {
                b.point(new ReplayPoint(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
            }
            // Клип без точок нікуди не дінеш: ReplayClip кидає виняток — пропускаємо такий.
            if (pointCount > 0) clips.add(b.build());
        }
        return ReplayScript.of(totalCut, clips);
    }
}
