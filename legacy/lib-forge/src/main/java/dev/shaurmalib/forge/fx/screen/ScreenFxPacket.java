package dev.shaurmalib.forge.fx.screen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клієнт: показати екранний ефект. Два види: пресет за id ({@code kind == null})
 * або ванільний урон ({@code kind} + урон + здоров'я після).
 */
public record ScreenFxPacket(String presetId, float intensity, ScreenFxDamageKind kind, float healthAfter) {

    public static ScreenFxPacket preset(String id, float intensity) { return new ScreenFxPacket(id, intensity, null, 0f); }

    public static ScreenFxPacket damage(ScreenFxDamageKind kind, float damage, float healthAfter) {
        return new ScreenFxPacket("", damage, kind, healthAfter);
    }

    public static void encode(ScreenFxPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.presetId);
        buf.writeFloat(p.intensity);
        buf.writeBoolean(p.kind != null);
        if (p.kind != null) {
            buf.writeEnum(p.kind);
            buf.writeFloat(p.healthAfter);
        }
    }

    public static ScreenFxPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        float intensity = buf.readFloat();
        if (buf.readBoolean()) return new ScreenFxPacket(id, intensity, buf.readEnum(ScreenFxDamageKind.class), buf.readFloat());
        return new ScreenFxPacket(id, intensity, null, 0f);
    }

    public static void handle(ScreenFxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (p.kind != null) ScreenFx.damage(p.kind, p.intensity, p.healthAfter);
            else ScreenFx.play(p.presetId, p.intensity);
        }));
        ctx.get().setPacketHandled(true);
    }
}
