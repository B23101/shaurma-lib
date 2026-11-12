package dev.shaurmalib.anim.fabric;

import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.emote.EmoteNetwork;
import dev.shaurmalib.anim.mc.AnimReloader;
import dev.shaurmalib.anim.mc.FirstPersonCompat;
import dev.shaurmalib.anim.mc.emote.EmoteClient;
import dev.shaurmalib.anim.mc.McAnim;
import dev.shaurmalib.anim.net.AnimMessage;
import io.netty.buffer.ByteBufInputStream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

/** Клієнтський клей Fabric: ресурси, тік, вихід, прийом пакетів — дзеркало {@code ForgeAnimClient}. */
public final class FabricAnimClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AnimReloader reloader = new AnimReloader();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return new ResourceLocation("shaurma_lib", "anim_clips"); }
            @Override public void onResourceManagerReload(ResourceManager manager) { reloader.onResourceManagerReload(manager); }
        });
        FirstPersonCompat.detect(FabricLoader.getInstance()::isModLoaded);
        KeyBindingHelper.registerKeyBinding(EmoteClient.KEY);
        EmoteNetwork.set(m -> {
            if (ClientPlayNetworking.canSend(FabricAnimBootstrap.EMOTE_CHANNEL)) {
                ClientPlayNetworking.send(FabricAnimBootstrap.EMOTE_CHANNEL, FabricAnimBootstrap.encode(m));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            McAnim.clientTick();
            EmoteClient.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> McAnim.onDisconnect());
        ClientPlayNetworking.registerGlobalReceiver(FabricAnimBootstrap.CHANNEL, (client, handler, buf, sender) -> {
            AnimMessage m;
            try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
                m = AnimMessage.read(in);
            } catch (IOException e) {
                return;
            }
            client.execute(() -> ShaurmaAnim.handle(m));
        });
    }
}
