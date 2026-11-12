package dev.shaurmalib.anim.forge;

import dev.shaurmalib.anim.emote.EmoteNetwork;
import dev.shaurmalib.anim.mc.emote.ServerEmotes;
import dev.shaurmalib.anim.net.AnimNetwork;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Клей рушія анімацій для Forge 1.20.1. Лише події/реєстрація — логіки тут немає
 * (вона в ядрі {@code dev.shaurmalib.anim} і в шарі версії {@code dev.shaurmalib.anim.mc}).
 */
public final class ForgeAnimBootstrap {

    private ForgeAnimBootstrap() {}

    /** З конструктора {@code @Mod}. */
    public static void init(IEventBus modBus) {
        // емоції: сервер гасить їх рухом / забороною мода
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ServerEmotes.tick(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ServerEmotes.onLogout(e.getEntity().getUUID()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ServerEmotes.onServerStopped());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeAnimClient.init(modBus));
    }

    /**
     * Реєстрація пакетів у каналі бібліотеки (у кінець, по порядку): {@code id} — анімація (сервер → клієнти),
     * {@code id+1} — запит емоції (клієнт → сервер). Повертає наступний вільний id.
     */
    public static int registerPackets(SimpleChannel channel, int id) {
        channel.registerMessage(id, AnimPacket.class, AnimPacket::encode, AnimPacket::decode, AnimPacket::handle);
        AnimNetwork.set((entity, message, includeSelf) -> {
            if (!(entity instanceof Entity e) || e.level().isClientSide) return;
            channel.send(includeSelf ? PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e)
                                     : PacketDistributor.TRACKING_ENTITY.with(() -> e), new AnimPacket(message));
        });
        channel.registerMessage(id + 1, EmotePacket.class, EmotePacket::encode, EmotePacket::decode, EmotePacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        EmoteNetwork.set(m -> channel.sendToServer(new EmotePacket(m)));
        return id + 2;
    }
}
