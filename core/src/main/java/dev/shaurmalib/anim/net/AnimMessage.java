package dev.shaurmalib.anim.net;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.runtime.PlayRequest;
import dev.shaurmalib.anim.runtime.Slot;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

/**
 * Мережеве повідомлення «зіграй/зупини анімацію на сутності» (сервер → усі, хто її бачить).
 * Формат дротів незалежний від лоадера: адаптер лише загортає байти у свій канал.
 *
 * {@code startAt} — скільки секунд кліп уже йде на сервері: гравець, що підключився посеред
 * дії, бачить її з правильного місця, а не з початку.
 */
public record AnimMessage(UUID entity, Op op, String clip, Slot slot, String mask,
                          float fadeIn, float fadeOut, float speed, float startAt, boolean restart,
                          AnimationClip.LoopMode loop) {

    public enum Op { PLAY, STOP_CLIP, STOP_SLOT, STOP_ALL }

    public static AnimMessage play(UUID entity, PlayRequestSpec s) {
        return new AnimMessage(entity, Op.PLAY, s.clip, s.slot, s.mask, s.fadeIn, s.fadeOut, s.speed, s.startAt, s.restart, s.loop);
    }

    public static AnimMessage stopClip(UUID entity, String clip, float fadeOut) {
        return new AnimMessage(entity, Op.STOP_CLIP, clip, null, null, Float.NaN, fadeOut, Float.NaN, 0, false, null);
    }

    public static AnimMessage stopSlot(UUID entity, Slot slot, float fadeOut) {
        return new AnimMessage(entity, Op.STOP_SLOT, "", slot, null, Float.NaN, fadeOut, Float.NaN, 0, false, null);
    }

    public static AnimMessage stopAll(UUID entity, float fadeOut) {
        return new AnimMessage(entity, Op.STOP_ALL, "", null, null, Float.NaN, fadeOut, Float.NaN, 0, false, null);
    }

    /** Серверний опис запиту (на сервері кліпів немає — лише id і параметри). */
    public static final class PlayRequestSpec {
        final String clip;
        Slot slot;
        String mask;
        float fadeIn = Float.NaN, fadeOut = Float.NaN, speed = Float.NaN, startAt;
        boolean restart;
        AnimationClip.LoopMode loop;

        private PlayRequestSpec(String clip) { this.clip = clip; }

        public static PlayRequestSpec of(String clip) { return new PlayRequestSpec(clip); }
        public PlayRequestSpec slot(Slot s) { slot = s; return this; }
        public PlayRequestSpec mask(String m) { mask = m; return this; }
        public PlayRequestSpec fade(float in, float out) { fadeIn = in; fadeOut = out; return this; }
        public PlayRequestSpec speed(float s) { speed = s; return this; }
        public PlayRequestSpec startAt(float s) { startAt = s; return this; }
        public PlayRequestSpec restart(boolean r) { restart = r; return this; }
        /** Перекрити режим повтору кліпа (null — як у файлі). Емоції: «зациклена» з реєстру. */
        public PlayRequestSpec loop(AnimationClip.LoopMode l) { loop = l; return this; }
    }

    public PlayRequest toRequest() {
        PlayRequest r = PlayRequest.of(clip).tag("net").restart(restart).startAt(startAt);
        if (slot != null) r.slot(slot);
        if (mask != null && !mask.isEmpty()) r.mask(mask);
        if (!Float.isNaN(fadeIn)) r.fadeIn(fadeIn);
        if (!Float.isNaN(fadeOut)) r.fadeOut(fadeOut);
        if (!Float.isNaN(speed)) r.speed(speed);
        if (loop != null) r.loop(loop);
        return r;
    }

    public void write(DataOutput out) throws IOException {
        out.writeLong(entity.getMostSignificantBits());
        out.writeLong(entity.getLeastSignificantBits());
        out.writeByte(op.ordinal());
        out.writeUTF(clip == null ? "" : clip);
        out.writeByte(slot == null ? -1 : slot.ordinal());
        out.writeUTF(mask == null ? "" : mask);
        out.writeFloat(fadeIn);
        out.writeFloat(fadeOut);
        out.writeFloat(speed);
        out.writeFloat(startAt);
        out.writeBoolean(restart);
        // дописано в кінець: старе повідомлення без цього байта читається як «режим з кліпа»
        out.writeByte(loop == null ? -1 : loop.ordinal());
    }

    public static AnimMessage read(DataInput in) throws IOException {
        UUID id = new UUID(in.readLong(), in.readLong());
        Op[] ops = Op.values();
        int opIdx = in.readByte();
        Op op = opIdx >= 0 && opIdx < ops.length ? ops[opIdx] : Op.STOP_ALL;
        String clip = in.readUTF();
        int s = in.readByte();
        Slot slot = s >= 0 && s < Slot.values().length ? Slot.values()[s] : null;
        String mask = in.readUTF();
        float fi = in.readFloat(), fo = in.readFloat(), sp = in.readFloat(), st = in.readFloat();
        boolean restart = in.readBoolean();
        AnimationClip.LoopMode loop = null;
        if (!(in instanceof java.io.InputStream is) || is.available() > 0) {
            int l = in.readByte();
            AnimationClip.LoopMode[] modes = AnimationClip.LoopMode.values();
            loop = l >= 0 && l < modes.length ? modes[l] : null;
        }
        return new AnimMessage(id, op, clip, slot, mask.isEmpty() ? null : mask, fi, fo, sp, st, restart, loop);
    }
}
