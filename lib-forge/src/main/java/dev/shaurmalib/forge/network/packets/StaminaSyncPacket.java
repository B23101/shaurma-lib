package dev.shaurmalib.forge.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class StaminaSyncPacket {
    private final boolean active;
    private final float stamina;
    private final float maxStamina;
    private final boolean blockJumpWhenDepleted;

    public StaminaSyncPacket(boolean active, float stamina, float maxStamina,
                             boolean blockJumpWhenDepleted) {
        this.active = active;
        this.stamina = stamina;
        this.maxStamina = maxStamina;
        this.blockJumpWhenDepleted = blockJumpWhenDepleted;
    }

    public static StaminaSyncPacket disabled() {
        return new StaminaSyncPacket(false, 0, 0, false);
    }

    public static void encode(StaminaSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeFloat(packet.stamina);
        buf.writeFloat(packet.maxStamina);
        buf.writeBoolean(packet.blockJumpWhenDepleted);
    }

    public static StaminaSyncPacket decode(FriendlyByteBuf buf) {
        return new StaminaSyncPacket(
                buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(StaminaSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientState.apply(packet));
        context.get().setPacketHandled(true);
    }

    public static final class ClientState {
        private static boolean active;
        private static float stamina;
        private static float maxStamina;
        private static boolean blockJumpWhenDepleted;

        private ClientState() {}

        private static void apply(StaminaSyncPacket packet) {
            active = packet.active;
            stamina = packet.stamina;
            maxStamina = packet.maxStamina;
            blockJumpWhenDepleted = packet.blockJumpWhenDepleted;
        }

        public static void clear() {
            active = false;
            stamina = 0;
            maxStamina = 0;
            blockJumpWhenDepleted = false;
        }

        public static boolean isActive() { return active; }
        public static float getStamina() { return stamina; }
        public static float getMaxStamina() { return maxStamina; }
        public static boolean shouldBlockJump() {
            return active && blockJumpWhenDepleted && stamina <= 0;
        }
    }
}
