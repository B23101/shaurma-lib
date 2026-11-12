package dev.shaurmalib.meta.forge;

import dev.shaurmalib.meta.MetaModes;
import dev.shaurmalib.meta.mc.McMetaClient;
import dev.shaurmalib.meta.mc.render.CosmeticRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.List;

/** Клієнтський Forge-клей: HUD, рендер світу, шар косметики, відтінок предметів, тік, вихід. */
final class ForgeMetaClient {

    private ForgeMetaClient() {}

    static void init(IEventBus modBus) {
        McMetaClient.init();
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> McMetaClient.onHud(e.getGuiGraphics(), e.getPartialTick()));
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            McMetaClient.onWorld(e.getPoseStack(), buffers, e.getCamera().getPosition(), e.getPartialTick());
            buffers.endBatch();
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> { if (e.phase == TickEvent.Phase.END) McMetaClient.onTick(); });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> McMetaClient.onDisconnect());
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> {
            for (String skin : e.getSkins()) {
                if (e.getSkin(skin) instanceof PlayerRenderer r) r.addLayer(new CosmeticRenderer.Layer(r));
            }
        });
        modBus.addListener((RegisterColorHandlersEvent.Item e) -> {
            List<Item> items = new ArrayList<>();
            for (var mode : MetaModes.all()) {
                for (String id : mode.cosmetics.models()) {
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    Item it = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
                    if (it != Items.AIR && !items.contains(it)) items.add(it);
                }
            }
            if (!items.isEmpty()) e.register((stack, tint) -> CosmeticRenderer.tint(stack), items.toArray(new Item[0]));
        });
    }

}
