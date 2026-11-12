package dev.shaurmalib.meta.mc;

import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.shaurmalib.meta.MetaMode;
import dev.shaurmalib.meta.MetaModes;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.server.MetaPlatform;
import dev.shaurmalib.meta.server.MetaServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Серверний клей мета-двигуна для 1.20.1 (однаковий для Forge і Fabric): платформа для {@link MetaServer},
 * вхід/вихід гравців, адмін-команда {@code /meta}. Лоадер лише викликає ці методи зі своїх подій.
 */
public final class McMeta {

    private static MinecraftServer server;

    private McMeta() {}

    public static MinecraftServer server() { return server; }

    /** Старт сервера: запускає рушій для режиму, що зареєстрований першим. */
    public static void onServerStarted(MinecraftServer s) {
        server = s;
        MetaMode mode = MetaModes.server();
        if (mode == null) return;
        MetaServer.start(mode, new MetaPlatform() {
            @Override public Path dataDir() { return s.getWorldPath(LevelResource.ROOT).resolve("data").resolve("shaurma_meta"); }
            @Override public Collection<UUID> online() {
                List<UUID> out = new ArrayList<>();
                for (ServerPlayer p : s.getPlayerList().getPlayers()) out.add(p.getUUID());
                return out;
            }
            @Override public boolean isAdmin(UUID id) {
                ServerPlayer p = s.getPlayerList().getPlayer(id);
                return p != null && p.hasPermissions(2);
            }
            @Override public void tell(UUID id, String text, boolean actionBar) {
                ServerPlayer p = s.getPlayerList().getPlayer(id);
                if (p != null) p.displayClientMessage(Component.literal(text), actionBar);
            }
        });
    }

    public static void onServerStopped() {
        MetaServer.stop();
        server = null;
    }

    public static void onJoin(ServerPlayer p) {
        MetaServer s = MetaServer.get();
        if (s == null) return;
        String skin = "";
        for (Property prop : p.getGameProfile().getProperties().get("textures")) { skin = prop.getValue(); break; }
        s.onJoin(p.getUUID(), p.getGameProfile().getName(), skin);
    }

    public static void onLeave(ServerPlayer p) {
        MetaServer s = MetaServer.get();
        if (s != null) s.onLeave(p.getUUID());
    }

    /** Гравці, яким слати подію біля точки (той самий світ, що й {@code near}; без нього — усі світи). */
    public static List<ServerPlayer> near(UUID nearId, double x, double y, double z, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        if (server == null) return out;
        ServerPlayer anchor = nearId == null ? null : server.getPlayerList().getPlayer(nearId);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (anchor != null && p.level() != anchor.level()) continue;
            if (p.distanceToSqr(x, y, z) <= r * r) out.add(p);
        }
        return out;
    }

    // ══ /meta ═══════════════════════════════════════════════════════════

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("meta").requires(s -> s.hasPermission(2))
                .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.literal("coins").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                        Reward.coins(MetaServer.get().mode.currency.id(), IntegerArgumentType.getInteger(c, "amount"))))
                                .then(Commands.argument("currency", StringArgumentType.word())
                                        .executes(c -> give(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                Reward.coins(StringArgumentType.getString(c, "currency"), IntegerArgumentType.getInteger(c, "amount")))))))
                        .then(Commands.literal("cosmetic").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    MetaServer s = MetaServer.get();
                                    if (s != null) s.mode.cosmetics.all().forEach(d -> b.suggest(d.id()));
                                    return b.buildFuture();
                                })
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                        Reward.cosmetic(StringArgumentType.getString(c, "id"))))))
                        .then(Commands.literal("material").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    MetaServer s = MetaServer.get();
                                    if (s != null) s.mode.materials.keySet().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(c -> give(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                Reward.material(StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "amount")))))))
                        .then(Commands.literal("points").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                        Reward.points(IntegerArgumentType.getInteger(c, "amount"))))))
                        .then(Commands.literal("xp").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    ServerPlayer t = EntityArgument.getPlayer(c, "player");
                                    int amount = IntegerArgumentType.getInteger(c, "amount");
                                    MetaServer.get().edit(t.getUUID(), p -> p.addXp("admin", amount));
                                    c.getSource().sendSuccess(() -> Component.literal("XP +" + amount + " → " + t.getGameProfile().getName()), true);
                                    return 1;
                                })))))
                .then(Commands.literal("open").then(Commands.argument("screen", StringArgumentType.word())
                        .suggests((c, b) -> { for (String s : List.of("profile", "wardrobe", "shop", "craft", "track", "upgrades", "leaderboard")) b.suggest(s); return b.buildFuture(); })
                        .executes(c -> {
                            MetaServer.get().open(c.getSource().getPlayerOrException().getUUID(), StringArgumentType.getString(c, "screen"));
                            return 1;
                        })))
                .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> {
                            ServerPlayer t = EntityArgument.getPlayer(c, "player");
                            MetaServer s = MetaServer.get();
                            s.edit(t.getUUID(), p -> {
                                p.xp.clear(); p.stats.view().keySet().stream().toList().forEach(k -> p.stats.set(k, 0));
                                p.wallet.view().keySet().stream().toList().forEach(k -> p.wallet.set(k, 0));
                                p.materials.clear(); p.owned.clear(); p.unseen.clear(); p.claimed.clear();
                                p.upgradePoints = 0; p.upgrades.clear();
                                p.wardrobe = new dev.shaurmalib.meta.cosmetics.Wardrobe();
                            });
                            s.onLeave(t.getUUID());
                            onJoin(t);
                            c.getSource().sendSuccess(() -> Component.literal("Профіль скинуто: " + t.getGameProfile().getName()), true);
                            return 1;
                        })));
    }

    private static int give(CommandSourceStack src, ServerPlayer target, Reward r) {
        MetaServer s = MetaServer.get();
        if (s == null) { src.sendFailure(Component.literal("Мета-двигун не запущено")); return 0; }
        if (r.type() == Reward.Type.COSMETIC && s.mode.cosmetics.get(r.id()) == null) {
            src.sendFailure(Component.literal("Нема такої косметики: " + r.id()));
            return 0;
        }
        s.grant(target.getUUID(), "Від адміністратора", r);
        src.sendSuccess(() -> Component.literal("Видано " + r.type() + " " + r.id() + " ×" + r.amount() + " → " + target.getGameProfile().getName()), true);
        return 1;
    }
}
