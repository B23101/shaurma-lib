package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.common.graffiti.GraffitiContentMode;
import dev.shaurmalib.common.graffiti.GraffitiSpec;
import dev.shaurmalib.common.graffiti.GraffitiTextLine;
import dev.shaurmalib.forge.graffiti.GraffitiClientCacheBridge;
import dev.shaurmalib.forge.graffiti.GraffitiCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер → клієнт: дані одного блоку графіті (позиція + всі
 * налаштування) — 1:1 перенесення {@code GraffitiSyncPacket}
 * snipers_shaurma у мережевий шар бібліотеки (план, п. 3.14). Клієнт
 * кладе/оновлює запис у своєму кеші рендера через
 * {@link GraffitiClientCacheBridge} (яку реєструє консюмер — сам пакет
 * не знає нічого про конкретний рендер/кеш, лише доставляє дані, той
 * самий принцип, що решта пакетів лібу). Текстура (лише для IMAGE-режиму)
 * запитується/кешується окремо через {@link GraffitiImageStartPacket}/
 * {@link GraffitiImageChunkPacket}.
 */
public class GraffitiSyncPacket {

    public final BlockPos pos;
    public final Direction side;
    public final GraffitiContentMode contentMode;
    public final String imageName;
    public final long fingerprint;
    public final List<GraffitiTextLine> textLines;
    public final float scale;
    public final float offsetX;
    public final float offsetY;
    public final float rotationDeg;

    public GraffitiSyncPacket(BlockPos pos, Direction side, GraffitiContentMode contentMode,
                               String imageName, long fingerprint, List<GraffitiTextLine> textLines,
                               float scale, float offsetX, float offsetY, float rotationDeg) {
        this.pos = pos;
        this.side = side;
        this.contentMode = contentMode == null ? GraffitiContentMode.IMAGE : contentMode;
        this.imageName = imageName == null ? "" : imageName;
        this.fingerprint = fingerprint;
        this.textLines = textLines == null ? List.of() : textLines;
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rotationDeg = rotationDeg;
    }

    public static GraffitiSyncPacket of(BlockPos pos, Direction side, GraffitiSpec spec, long fingerprint) {
        return new GraffitiSyncPacket(pos, side, spec.contentMode(), spec.imageName(), fingerprint,
                spec.textLines(), spec.scale(), spec.offsetX(), spec.offsetY(), spec.rotationDeg());
    }

    public static void encode(GraffitiSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeEnum(pkt.side);
        buf.writeEnum(pkt.contentMode);
        buf.writeUtf(pkt.imageName, 128);
        buf.writeLong(pkt.fingerprint);
        buf.writeVarInt(pkt.textLines.size());
        for (GraffitiTextLine line : pkt.textLines) GraffitiCodec.writeTextLine(buf, line);
        buf.writeFloat(pkt.scale);
        buf.writeFloat(pkt.offsetX);
        buf.writeFloat(pkt.offsetY);
        buf.writeFloat(pkt.rotationDeg);
    }

    public static GraffitiSyncPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction side = buf.readEnum(Direction.class);
        GraffitiContentMode mode = buf.readEnum(GraffitiContentMode.class);
        String imageName = buf.readUtf(128);
        long fingerprint = buf.readLong();
        int n = buf.readVarInt();
        List<GraffitiTextLine> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(GraffitiCodec.readTextLine(buf));
        float scale = buf.readFloat();
        float ox = buf.readFloat();
        float oy = buf.readFloat();
        float rot = buf.readFloat();
        return new GraffitiSyncPacket(pos, side, mode, imageName, fingerprint, lines, scale, ox, oy, rot);
    }

    public static void handle(GraffitiSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            GraffitiClientCacheBridge.dispatchSync(pkt);
        });
        ctx.get().setPacketHandled(true);
    }
}
