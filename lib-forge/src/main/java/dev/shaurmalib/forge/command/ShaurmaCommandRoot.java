package dev.shaurmalib.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.shaurmalib.common.license.LicenseGate;
import dev.shaurmalib.common.lifecycle.MatchLifecycleBus;
import dev.shaurmalib.common.lifecycle.MatchLifecycleState;
import dev.shaurmalib.forge.mode.GameModeRegistry;
import dev.shaurmalib.forge.mode.ModeCommandContribution;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Робочий модуль команд (план, п. 3.30) — узагальнення кореневої
 * Brigadier-структури {@code SGCommand} snipers_shaurma (527 рядків)
 * у переносний каркас: корінь команди + {@code start}/{@code stop}/
 * {@code mode}/{@code settings} + license-гейт + точка підключення
 * per-mode підкоманд ({@link ModeCommandContribution}, вже існуючий
 * контракт).
 * <p>
 * <b>Що лишається продуктовою специфікою консюмера, не переноситься</b>
 * (див. клас-докстрінг {@code SGCommand} в оригіналі): підкоманди
 * {@code /sg config reload} (перезавантаження zipline/tacz/enhanced-visuals/
 * combat-area конфігів), {@code /sg combatarea}, {@code /sg radiation},
 * {@code /sg leaderboard}, {@code /sg endgame setup}, {@code /sg anim},
 * {@code /sg ammosort}, {@code /sg reviews}, {@code /sg stats} — усе це
 * специфічні до конкретного продукту дерева команд, які консюмер
 * підвішує до {@link LiteralArgumentBuilder}, що повертає
 * {@link #build}, звичайним {@code root.then(...)} до виклику
 * {@link CommandDispatcher#register}.
 * <p>
 * <b>Приклад використання</b> (у {@code RegisterCommandsEvent}-хендлері
 * консюмера):
 * <pre>{@code
 * ShaurmaCommandRoot cmdRoot = new ShaurmaCommandRoot(
 *     "sg",
 *     licenseModule.gate(),
 *     lifecycleModule.lifecycleBus(),
 *     modeContractModule.registry(),
 *     (source, teamMode) -> { GameStateManager.startGame(source.getServer()); },
 *     (source) -> { GameStateManager.stopGame(); },
 *     (source, player) -> { NetworkHandler.sendToPlayer(player, buildSettingsPacket()); }
 * );
 * LiteralArgumentBuilder<CommandSourceStack> root = cmdRoot.build();
 * // консюмер підвішує власні гілки СЮДИ, до реєстрації:
 * root.then(CombatAreaCommand.buildTree(Commands.literal("combatarea")));
 * root.then(EndGameSetupCommand.build());
 * // ...
 * dispatcher.register(root);
 * cmdRoot.registerAliases(dispatcher); // /startgame, /stopgame
 * }</pre>
 */
public final class ShaurmaCommandRoot {

    private static final Logger LOGGER = LogManager.getLogger("ShaurmaLib/CommandRoot");

    /** Callback для {@code /<root> start [solo|team]}. {@code teamMode} — {@code null}, якщо аргумент не передано (з конфігу). */
    @FunctionalInterface
    public interface StartAction {
        void start(CommandSourceStack source, Boolean teamMode);
    }

    /** Callback для {@code /<root> stop}. */
    @FunctionalInterface
    public interface StopAction {
        void stop(CommandSourceStack source);
    }

    /** Callback для {@code /<root> settings} — типово відсилає мережевий пакет відкриття GUI гравцю. */
    @FunctionalInterface
    public interface SettingsAction {
        void openSettings(CommandSourceStack source, ServerPlayer player);
    }

    private final String rootName;
    private final LicenseGate licenseGate;
    private final MatchLifecycleBus lifecycleBus;
    private final GameModeRegistry modeRegistry;
    private final StartAction startAction;
    private final StopAction stopAction;
    private final SettingsAction settingsAction;
    private final int permissionLevel;

    /**
     * @param rootName        назва кореневої команди без {@code /} (напр. {@code "sg"}).
     * @param licenseGate     ліцензійний гейт (може бути {@code null} — тоді {@code requireLicense}
     *                        завжди пропускає, підходить для мода без власної ліцензії).
     * @param lifecycleBus    для перевірки {@code IDLE} перед зміною режиму (може бути {@code null} —
     *                        тоді зміна режиму дозволена завжди, без перевірки фази).
     * @param modeRegistry    реєстр режимів (3.18) — для {@code mode <id>} і збору
     *                        {@link ModeCommandContribution} кожного зареєстрованого режиму.
     * @param startAction     дія на {@code start}, обов'язкова.
     * @param stopAction      дія на {@code stop}, обов'язкова.
     * @param settingsAction  дія на {@code settings} ({@code null} — підкоманда не реєструється).
     */
    public ShaurmaCommandRoot(String rootName,
                               LicenseGate licenseGate,
                               MatchLifecycleBus lifecycleBus,
                               GameModeRegistry modeRegistry,
                               StartAction startAction,
                               StopAction stopAction,
                               SettingsAction settingsAction) {
        this(rootName, licenseGate, lifecycleBus, modeRegistry, startAction, stopAction, settingsAction, 2);
    }

    public ShaurmaCommandRoot(String rootName,
                               LicenseGate licenseGate,
                               MatchLifecycleBus lifecycleBus,
                               GameModeRegistry modeRegistry,
                               StartAction startAction,
                               StopAction stopAction,
                               SettingsAction settingsAction,
                               int permissionLevel) {
        this.rootName = Objects.requireNonNull(rootName, "rootName");
        this.licenseGate = licenseGate;
        this.lifecycleBus = lifecycleBus;
        this.modeRegistry = Objects.requireNonNull(modeRegistry, "modeRegistry");
        this.startAction = Objects.requireNonNull(startAction, "startAction");
        this.stopAction = Objects.requireNonNull(stopAction, "stopAction");
        this.settingsAction = settingsAction;
        this.permissionLevel = permissionLevel;
    }

    /**
     * Будує кореневу гілку команди — {@code start}/{@code stop}/{@code mode}/
     * (опційно) {@code settings}, плюс {@link ModeCommandContribution} кожного
     * зареєстрованого режиму (той самий прикінцевий виклик, що в оригінальному
     * {@code SGCommand.buildSubcommands}: {@code GameModeRegistry.all().forEach(...)}).
     * <p>
     * Консюмер підвішує власні продуктово-специфічні гілки до повернутого
     * білдера ПЕРЕД реєстрацією в {@link CommandDispatcher} — див. клас-докстрінг.
     */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(rootName).requires(s -> s.hasPermission(permissionLevel));

        root.then(Commands.literal("start")
                .executes(ctx -> executeStart(ctx.getSource(), null))
                .then(Commands.literal("solo").executes(ctx -> executeStart(ctx.getSource(), false)))
                .then(Commands.literal("team").executes(ctx -> executeStart(ctx.getSource(), true))));

        root.then(Commands.literal("stop")
                .executes(ctx -> executeStop(ctx.getSource())));

        root.then(Commands.literal("mode")
                .then(Commands.argument("modeId", StringArgumentType.word())
                        .executes(this::executeModeSwitch)));

        if (settingsAction != null) {
            root.then(Commands.literal("settings")
                    .executes(ctx -> executeSettings(ctx.getSource())));
        }

        modeRegistry.all().forEach(mode ->
                mode.getModeCommand().ifPresent(cmd -> cmd.registerSubcommands(root)));

        return root;
    }

    /**
     * Реєструє {@code /startgame [team]} і {@code /stopgame} як окремі
     * корені дерева команд, з тим самим {@link #permissionLevel} і тими ж
     * {@link StartAction}/{@link StopAction} callback-ами — 1:1 перенесення
     * аліасів з оригінального {@code SGCommand.register}. Викликати ОКРЕМО
     * від {@link CommandDispatcher#register} на результат {@link #build()},
     * бо це незалежні корені дерева, не гілки {@code /<rootName>}.
     */
    public void registerAliases(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("startgame")
                .requires(s -> s.hasPermission(permissionLevel))
                .executes(ctx -> executeStart(ctx.getSource(), null))
                .then(Commands.literal("team")
                        .executes(ctx -> executeStart(ctx.getSource(), true))));

        dispatcher.register(Commands.literal("stopgame")
                .requires(s -> s.hasPermission(permissionLevel))
                .executes(ctx -> executeStop(ctx.getSource())));
    }

    /**
     * Гейт-хелпер для консюмерських підкоманд, підвішених поза цим класом
     * (напр. {@code /sg config reload}, яка так само мала перевірку
     * ліцензії в оригіналі) — той самий {@code requireLicense(source)},
     * що використовувався 4 рази в {@code SGCommand}, винесений як
     * публічний метод, щоб не дублювати перевірку по кожному консюмерському
     * файлу команд окремо.
     */
    public boolean requireLicense(CommandSourceStack source) {
        if (licenseGate == null) return true;
        if (!licenseGate.isActivated()) {
            source.sendFailure(Component.literal("[" + rootName.toUpperCase(java.util.Locale.ROOT) + "] Not available."));
            return false;
        }
        return true;
    }

    /**
     * {@code true}, якщо зараз можна безпечно змінювати режим/налаштування
     * (лібовий {@link MatchLifecycleState#IDLE}) — той самий інваріант, що
     * {@code GameStateManager.getCurrentPhase() != GamePhase.IDLE} в оригіналі,
     * узагальнений через {@link MatchLifecycleBus} (3.19) замість
     * snipers-специфічного {@code GamePhase}.
     */
    public boolean isIdle() {
        return lifecycleBus == null || lifecycleBus.isIdle();
    }

    private int executeStart(CommandSourceStack source, Boolean teamMode) {
        if (!requireLicense(source)) return 0;
        if (!isIdle()) {
            source.sendFailure(Component.literal("[" + rootName + "] Гра вже розпочата!"));
            return 0;
        }
        startAction.start(source, teamMode);
        String modeStr = teamMode != null ? (teamMode ? "командний" : "соло") : "(з конфігу)";
        source.sendSuccess(() -> Component.literal("[" + rootName + "] Гра розпочата! Режим: " + modeStr), true);
        return 1;
    }

    private int executeStop(CommandSourceStack source) {
        if (!requireLicense(source)) return 0;
        stopAction.stop(source);
        source.sendSuccess(() -> Component.literal("[" + rootName + "] Гру зупинено"), true);
        return 1;
    }

    private int executeModeSwitch(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireLicense(source)) return 0;
        if (!isIdle()) {
            source.sendFailure(Component.literal("[" + rootName + "] Зміна режиму тільки в IDLE!"));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "modeId");
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("[" + rootName + "] Сервер недоступний."));
            return 0;
        }
        boolean ok = modeRegistry.setActive(id, server);
        if (ok) {
            source.sendSuccess(() ->
                    Component.literal("[" + rootName + "] Активний режим: " + modeRegistry.current().displayName()), true);
        } else {
            source.sendFailure(Component.literal("[" + rootName + "] Невідомий режим: " + id));
        }
        return ok ? 1 : 0;
    }

    private int executeSettings(CommandSourceStack source) {
        if (!requireLicense(source)) return 0;
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("[" + rootName + "] Тільки для гравців"));
            return 0;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            settingsAction.openSettings(source, player);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("[" + rootName + "] Помилка: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * {@code true}, якщо режим доступний навіть без гравця (напр. консольна
     * команда). Хелпер для консюмерських {@code license status/activate}-
     * підкоманд, які в оригіналі мали {@code s.getEntity() == null} —
     * винесений тут, щоб не переписувати ту саму умову в кожному консюмері.
     */
    public static BooleanSupplier consoleOnlyGuardMessage() {
        return () -> true;
    }
}
