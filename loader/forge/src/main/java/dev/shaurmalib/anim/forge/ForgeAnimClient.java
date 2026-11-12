package dev.shaurmalib.anim.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.mc.AnimReloader;
import dev.shaurmalib.anim.mc.FirstPersonCompat;
import dev.shaurmalib.anim.mc.emote.EmoteClient;
import dev.shaurmalib.anim.mc.McAnim;
import dev.shaurmalib.anim.runtime.Animator;
import dev.shaurmalib.anim.runtime.ClipPlayer;
import dev.shaurmalib.anim.runtime.PlayRequest;
import dev.shaurmalib.anim.runtime.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

import java.util.Arrays;
import java.util.Locale;

/** Клієнтська частина клею Forge: перезавантаження ресурсів, тік, вихід зі світу, команда {@code /sanim}. */
final class ForgeAnimClient {

    private ForgeAnimClient() {}

    static void init(IEventBus modBus) {
        FirstPersonCompat.detect(id -> ModList.get().isLoaded(id));
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new AnimReloader()));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(EmoteClient.KEY));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) return;
            McAnim.clientTick();
            EmoteClient.tick();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> McAnim.onDisconnect());
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> registerCommand(e.getDispatcher()));
    }

    /**
     * {@code /sanim play <clip> [slot]} — програти на собі (локально, для перевірки кліпа з Blockbench);
     * {@code /sanim stop}, {@code /sanim list [фільтр]}, {@code /sanim info}, {@code /sanim locomotion on|off}, {@code /sanim emotes} — налаштувати колесо.
     */
    private static void registerCommand(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sanim")
            .then(Commands.literal("play")
                .then(Commands.argument("clip", ResourceLocationArgument.id())
                    .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                        ShaurmaAnim.clips().all().stream().map(cl -> net.minecraft.resources.ResourceLocation.tryParse(cl.id()))
                            .filter(java.util.Objects::nonNull), b))
                    .executes(c -> play(c, null))
                    .then(Commands.argument("slot", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                            Arrays.stream(Slot.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), b))
                        .executes(c -> play(c, StringArgumentType.getString(c, "slot"))))))
            .then(Commands.literal("stop").executes(c -> {
                Animator a = local();
                if (a != null) a.stopAll(0.2f);
                return 1;
            }))
            .then(Commands.literal("list")
                .executes(c -> list(c, ""))
                .then(Commands.argument("filter", StringArgumentType.greedyString())
                    .executes(c -> list(c, StringArgumentType.getString(c, "filter")))))
            .then(Commands.literal("info").executes(ForgeAnimClient::info))
            .then(Commands.literal("emotes").executes(c -> {
                // чат закривається ПІСЛЯ команди — екран ставимо наступною задачею
                Minecraft.getInstance().tell(() -> EmoteClient.openSettings(-1));
                return 1;
            }))
            .then(Commands.literal("locomotion")
                .then(Commands.literal("on").executes(c -> { ShaurmaAnim.setPlayerLocomotion(true); return 1; }))
                .then(Commands.literal("off").executes(c -> { ShaurmaAnim.setPlayerLocomotion(false); return 1; }))));
    }

    private static Animator local() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : McAnim.animator(p);
    }

    private static int play(CommandContext<CommandSourceStack> c, String slot) {
        String id = ResourceLocationArgument.getId(c, "clip").toString();
        Animator a = local();
        if (a == null) return 0;
        PlayRequest r = PlayRequest.of(id).restart(true);
        if (slot != null) r.slot(Slot.parse(slot, Slot.ACTION));
        boolean ok = a.play(r);
        say(c, ok ? "▶ " + id : "Немає кліпа " + id + " (див. /sanim list)");
        return ok ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> c, String filter) {
        var ids = ShaurmaAnim.clips().all().stream().map(AnimationClip::id).filter(s -> s.contains(filter)).sorted().toList();
        say(c, ids.size() + " кліпів" + (filter.isEmpty() ? "" : " з '" + filter + "'"));
        ids.stream().limit(40).forEach(s -> say(c, " " + s));
        return ids.size();
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        ShaurmaAnim.debugLines().forEach(l -> say(c, l));
        Animator a = local();
        if (a != null) {
            say(c, "rig=" + a.rig().name());
            for (Slot s : Slot.values()) {
                ClipPlayer p = a.current(s);
                if (p != null) say(c, " " + s + ": " + p.clip().id() + " [" + p.tag() + "]");
            }
        }
        return 1;
    }

    private static void say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSystemMessage(Component.literal(s));
    }
}
