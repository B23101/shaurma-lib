package dev.shaurmalib.anim.fabric;

import dev.shaurmalib.anim.emote.EmoteMessage;
import dev.shaurmalib.anim.mc.emote.ServerEmotes;
import dev.shaurmalib.anim.net.AnimMessage;
import dev.shaurmalib.anim.net.AnimNetwork;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Клей рушія анімацій для Fabric 1.20.1 — той самий обсяг, що й {@code ForgeAnimBootstrap}:
 * лише мережа (сервер → усі, хто бачить сутність). Логіка — в ядрі і в шарі версії.
 */
public final class FabricAnimBootstrap implements ModInitializer {

    public static final ResourceLocation CHANNEL = new ResourceLocation("shaurma_lib", "anim");
    /** Клієнт → сервер: запит емоції. */
    public static final ResourceLocation EMOTE_CHANNEL = new ResourceLocation("shaurma_lib", "emote");

    @Override
    public void onInitialize() {
        AnimNetwork.set((entity, message, includeSelf) -> {
            if (!(entity instanceof Entity e) || e.level().isClientSide) return;
            FriendlyByteBuf buf = encode(message);
            for (ServerPlayer p : PlayerLookup.tracking(e)) ServerPlayNetworking.send(p, CHANNEL, new FriendlyByteBuf(buf.copy()));
            if (includeSelf && e instanceof ServerPlayer self) ServerPlayNetworking.send(self, CHANNEL, new FriendlyByteBuf(buf.copy()));
        });
        ServerPlayNetworking.registerGlobalReceiver(EMOTE_CHANNEL, (server, player, handler, buf, sender) -> {
            EmoteMessage m;
            try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
                m = EmoteMessage.read(in);
            } catch (IOException ex) {
                return;
            }
            server.execute(() -> ServerEmotes.handle(player, m));
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerEmotes::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerEmotes.onLogout(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerEmotes.onServerStopped());
    }

    static FriendlyByteBuf encode(EmoteMessage m) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
            m.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buf;
    }

    static FriendlyByteBuf encode(AnimMessage m) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
            m.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buf;
    }
}
