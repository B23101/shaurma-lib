package dev.shaurmalib.meta.fabric;

import dev.shaurmalib.meta.MetaModes;
import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.mc.McMetaClient;
import dev.shaurmalib.meta.mc.render.CosmeticRenderer;
import dev.shaurmalib.meta.net.MetaMessage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;

/** Fabric-клей мета-двигуна (клієнт): дзеркало {@code ForgeMetaClient}. */
public final class FabricMetaClient implements ClientModInitializer {

    private static final Set<Item> TINTED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        McMetaClient.init();
        ClientPlayNetworking.registerGlobalReceiver(FabricMetaBootstrap.CHANNEL, (client, handler, buf, sender) -> {
            MetaMessage m = FabricMetaBootstrap.decode(buf);
            if (m != null && !m.op().toServer()) client.execute(() -> MetaClient.handle(m));
        });
        HudRenderCallback.EVENT.register(McMetaClient::onHud);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (ctx.consumers() == null) return;
            McMetaClient.onWorld(ctx.matrixStack(), ctx.consumers(), ctx.camera().getPosition(), ctx.tickDelta());
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> { registerTints(); McMetaClient.onTick(); });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> McMetaClient.onDisconnect());
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerRenderer r) helper.register(new CosmeticRenderer.Layer(r));
        });
    }

    /**
     * Відтінок предметів-моделей косметики. На Fabric порядок entrypoint-ів модів не гарантований, тож
     * реєструємо ліниво, коли режим уже зареєстрував каталог (реєстр ColorProvider приймає й пізню реєстрацію).
     */
    private static void registerTints() {
        for (var mode : MetaModes.all()) {
            for (String id : mode.cosmetics.models()) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                Item it = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
                if (it == Items.AIR || !TINTED.add(it)) continue;
                ColorProviderRegistry.ITEM.register((stack, tint) -> CosmeticRenderer.tint(stack), it);
            }
        }
    }

    static void send(MetaMessage m) {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(FabricMetaBootstrap.CHANNEL)) {
            ClientPlayNetworking.send(FabricMetaBootstrap.CHANNEL, FabricMetaBootstrap.encode(m));
        }
    }
}
