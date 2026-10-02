package dev.shaurmalib.forge.offline;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.shaurmalib.common.offline.OfflineCloseReason;
import dev.shaurmalib.common.offline.OfflineRecord;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Налагоджувальні команди офлайн-присутності без другого клієнта (план, §5.10).
 * Підключаються в {@code ShaurmaCommandRoot#build} як гілка {@code offline}
 * (тож {@code /<root> offline ...}); доступ має той самий рівень прав, що й корінь.
 * <pre>
 *   offline list
 *   offline spawn &lt;player&gt;     тіло з поточного гравця, не виходячи (нічого не дропає)
 *   offline kill &lt;name&gt;        вбити тіло з дропом
 *   offline discard &lt;name&gt;     прибрати тіло безшумно
 *   offline purge               прибрати всі тіла безшумно
 *   offline scope open|close
 * </pre>
 * Якщо модуль не ввімкнено ({@code withOfflinePresence}), команди відповідають помилкою.
 */
public final class OfflineDebugCommand {

    private OfflineDebugCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("offline")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> spawn(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("kill")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> kill(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("discard")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> discard(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("purge").executes(ctx -> purge(ctx.getSource())))
                .then(Commands.literal("scope")
                        .then(Commands.literal("open").executes(ctx -> scopeOpen(ctx.getSource())))
                        .then(Commands.literal("close").executes(ctx -> scopeClose(ctx.getSource()))));
    }

    private static OfflinePresenceModule module(CommandSourceStack source) {
        OfflinePresenceModule module = OfflinePresenceModule.current();
        if (module == null) {
            source.sendFailure(Component.literal("Офлайн-присутність не ввімкнено (withOfflinePresence)."));
        }
        return module;
    }

    private static int list(CommandSourceStack source) {
        OfflinePresenceModule module = module(source);
        if (module == null) {
            return 0;
        }
        Collection<OfflineRecord> records = module.records();
        int now = source.getServer().getTickCount();
        String header = "Scope " + (module.isScopeOpen() ? "відкрито" : "закрито") + ", записів: " + records.size();
        source.sendSuccess(() -> Component.literal(header), false);
        for (OfflineRecord r : records) {
            String line = r.ownerName() + " [" + r.status() + "] " + r.dimensionKey()
                    + String.format(" (%.1f, %.1f, %.1f)", r.x(), r.y(), r.z())
                    + ", відсутній " + Math.max(0, now - r.loggedOutTick()) + " тік.";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return records.size();
    }

    private static int spawn(CommandSourceStack source, ServerPlayer player) {
        OfflinePresenceModule module = module(source);
        if (module == null) {
            return 0;
        }
        if (!module.isScopeOpen()) {
            source.sendFailure(Component.literal("Scope закрито: спершу /… offline scope open."));
            return 0;
        }
        Optional<OfflineRecord> record = module.spawnDebug(player);
        if (record.isEmpty()) {
            source.sendFailure(Component.literal("Не вдалося створити тіло."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Тіло створено для " + player.getGameProfile().getName()
                + " (без дропу)."), true);
        return 1;
    }

    private static Optional<UUID> ownerByName(OfflinePresenceModule module, String name) {
        for (OfflineRecord r : module.records()) {
            if (r.ownerName().equalsIgnoreCase(name)) {
                return Optional.of(r.owner());
            }
        }
        return Optional.empty();
    }

    private static int kill(CommandSourceStack source, String name) {
        OfflinePresenceModule module = module(source);
        if (module == null) {
            return 0;
        }
        Optional<UUID> owner = ownerByName(module, name);
        if (owner.isEmpty() || !module.kill(owner.get(), null)) {
            source.sendFailure(Component.literal("Живого тіла з таким іменем немає: " + name));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Тіло " + name + " вбито."), true);
        return 1;
    }

    private static int discard(CommandSourceStack source, String name) {
        OfflinePresenceModule module = module(source);
        if (module == null) {
            return 0;
        }
        Optional<UUID> owner = ownerByName(module, name);
        if (owner.isEmpty() || !module.discardSilently(owner.get())) {
            source.sendFailure(Component.literal("Запису з таким іменем немає: " + name));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Тіло " + name + " прибрано."), true);
        return 1;
    }

    private static int purge(CommandSourceStack source) {
        OfflinePresenceModule module = module(source);
        if (module == null) {
            return 0;
        }
        int removed = 0;
        for (OfflineRecord r : module.records()) {
            if (module.discardSilently(r.owner())) {
                removed++;
            }
        }
        int total = removed;
        source.sendSuccess(() -> Component.literal("Прибрано тіл: " + total), true);
        return removed;
    }

    private static int scopeOpen(CommandSourceStack source) {
        OfflinePresenceModule module = module(source);
        if (module == null) {
            return 0;
        }
        module.openScope();
        source.sendSuccess(() -> Component.literal("Scope відкрито."), true);
        return 1;
    }

    private static int scopeClose(CommandSourceStack source) {
        OfflinePresenceModule module = module(source);
        if (module == null) {
            return 0;
        }
        int removed = module.closeScope(OfflineCloseReason.MANUAL);
        source.sendSuccess(() -> Component.literal("Scope закрито, тіл прибрано: " + removed), true);
        return 1;
    }
}
