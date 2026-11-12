package dev.shaurmalib.meta.fabric;

import dev.shaurmalib.meta.mc.McMeta;
import dev.shaurmalib.meta.net.MetaMessage;
import dev.shaurmalib.meta.net.MetaNetwork;
import dev.shaurmalib.meta.server.MetaServer;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/** Fabric-клей мета-двигуна (сервер): дзеркало {@code ForgeMetaBootstrap}. */
public final class FabricMetaBootstrap implements ModInitializer {

    public static final ResourceLocation CHANNEL = new ResourceLocation("shaurma_lib", "meta");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(McMeta::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> McMeta.onServerStopped());
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> McMeta.onJoin(h.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> McMeta.onLeave(h.getPlayer()));
        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(McMeta.command()));
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) -> {
            MetaMessage m = decode(buf);
            if (m == null || !m.op().toServer()) return;
            server.execute(() -> {
                MetaServer s = MetaServer.get();
                if (s != null) s.handle(player.getUUID(), m);
            });
        });
        MetaNetwork.set(new MetaNetwork() {
            @Override public void toPlayer(UUID id, MetaMessage m) {
                ServerPlayer p = McMeta.server() == null ? null : McMeta.server().getPlayerList().getPlayer(id);
                if (p != null) ServerPlayNetworking.send(p, CHANNEL, encode(m));
            }
            @Override public void toAll(MetaMessage m) {
                if (McMeta.server() == null) return;
                FriendlyByteBuf buf = encode(m);
                for (ServerPlayer p : McMeta.server().getPlayerList().getPlayers()) ServerPlayNetworking.send(p, CHANNEL, new FriendlyByteBuf(buf.copy()));
            }
            @Override public void toNear(UUID near, double x, double y, double z, double r, MetaMessage m) {
                FriendlyByteBuf buf = encode(m);
                for (ServerPlayer p : McMeta.near(near, x, y, z, r)) ServerPlayNetworking.send(p, CHANNEL, new FriendlyByteBuf(buf.copy()));
            }
            @Override public void toServer(MetaMessage m) { FabricMetaClient.send(m); }
        });
    }

    static FriendlyByteBuf encode(MetaMessage m) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
            m.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buf;
    }

    static MetaMessage decode(FriendlyByteBuf buf) {
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            return MetaMessage.read(in);
        } catch (IOException e) {
            return null;
        }
    }
}
