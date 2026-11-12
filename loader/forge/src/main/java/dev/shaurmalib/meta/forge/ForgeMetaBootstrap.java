package dev.shaurmalib.meta.forge;

import dev.shaurmalib.meta.mc.McMeta;
import dev.shaurmalib.meta.net.MetaMessage;
import dev.shaurmalib.meta.net.MetaNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Forge-клей мета-двигуна: події сервера → {@link McMeta}, мережа → {@link MetaNetwork}.
 * Викликається з конструктора мода бібліотеки ({@link #init}) і з реєстрації каналу ({@link #registerPackets}).
 */
public final class ForgeMetaBootstrap {

    private ForgeMetaBootstrap() {}

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> McMeta.onServerStarted(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> McMeta.onServerStopped());
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) McMeta.onJoin(p);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) McMeta.onLeave(p);
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> e.getDispatcher().register(McMeta.command()));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeMetaClient.init(modBus));
    }

    /** Один двонаправлений пакет на спільному каналі бібліотеки. @return наступний вільний id */
    public static int registerPackets(SimpleChannel channel, int id) {
        channel.registerMessage(id, MetaPacket.class, MetaPacket::encode, MetaPacket::decode, MetaPacket::handle);
        MetaNetwork.set(new MetaNetwork() {
            @Override public void toPlayer(UUID player, MetaMessage m) {
                ServerPlayer p = player(player);
                if (p != null) channel.send(PacketDistributor.PLAYER.with(() -> p), new MetaPacket(m));
            }
            @Override public void toAll(MetaMessage m) {
                if (McMeta.server() != null) channel.send(PacketDistributor.ALL.noArg(), new MetaPacket(m));
            }
            @Override public void toNear(UUID near, double x, double y, double z, double r, MetaMessage m) {
                for (ServerPlayer p : McMeta.near(near, x, y, z, r)) channel.send(PacketDistributor.PLAYER.with(() -> p), new MetaPacket(m));
            }
            @Override public void toServer(MetaMessage m) { channel.sendToServer(new MetaPacket(m)); }
        });
        return id + 1;
    }

    private static ServerPlayer player(UUID id) {
        return McMeta.server() == null ? null : McMeta.server().getPlayerList().getPlayer(id);
    }
}
