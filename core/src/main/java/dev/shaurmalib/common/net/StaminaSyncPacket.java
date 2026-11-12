package dev.shaurmalib.common.net;

import dev.shaurmalib.platform.net.PacketCodec;

/** Перенесено з forge.network.packets.StaminaSyncPacket без змін формату на дроті. */
public record StaminaSyncPacket(boolean active, float stamina, float maxStamina,
                                boolean blockJumpWhenDepleted, boolean sprintBlocked) {
    public static StaminaSyncPacket disabled() { return new StaminaSyncPacket(false, 0, 0, false, false); }

    public static final PacketCodec<StaminaSyncPacket> CODEC = new PacketCodec<>() {
        @Override public void encode(StaminaSyncPacket p, dev.shaurmalib.platform.net.ByteSink b) {
            b.writeBool(p.active); b.writeFloat(p.stamina); b.writeFloat(p.maxStamina);
            b.writeBool(p.blockJumpWhenDepleted); b.writeBool(p.sprintBlocked);
        }
        @Override public StaminaSyncPacket decode(dev.shaurmalib.platform.net.ByteSource b) {
            return new StaminaSyncPacket(b.readBool(), b.readFloat(), b.readFloat(), b.readBool(), b.readBool());
        }
    };
}
