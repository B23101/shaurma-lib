package dev.shaurmalib.forge.mode;

import dev.shaurmalib.common.lifecycle.DisconnectPolicy;
import dev.shaurmalib.common.lifecycle.JoinPolicy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Реєструє разом join/return-поведінку гравця (план, п. 3.8 —
 * "гравці, які зайшли під час активної гри" + "що робити з гравцями, які
 * вийшли — дати повернення чи інше"). {@link PlayerJoinFlow} і
 * {@link PlayerReturnFlow} існували в бібліотеці й раніше як самостійні
 * класи, але не мали точки підключення в {@link dev.shaurmalib.forge.ShaurmaLib.Builder}
 * — цей модуль саме така точка, у тому самому стилі, що й решта
 * {@code withXxx(...)}-модулів (свідоме підключення, зрозуміла помилка,
 * якщо консюмер звертається до модуля без попереднього
 * {@code withPlayerLifecycle(...)}).
 * <p>
 * Це навмисно ОКРЕМИЙ прапорець від {@link LifecycleModule}
 * ({@code withLifecycle(...)}) — {@link LifecycleModule} відповідає за
 * грубий {@code MatchLifecycleState} матчу в цілому (чи можна редагувати
 * налаштування, чи почати лобі-табло), а цей модуль — за долю конкретного
 * гравця відносно матчу (спостерігач/черга/відмова при вході,
 * відновлення/скидання/вибуття при поверненні). Консюмер може захотіти
 * лише одне з двох (наприклад режим без матчів взагалі не потребує
 * join/return-політик), тому вони не об'єднані в один прапорець.
 */
public final class PlayerLifecycleModule {

    private final JoinPolicy joinPolicy;
    private final Component joinRejectMessage;
    private final DisconnectPolicy disconnectPolicy;
    private final PlayerReturnFlow returnFlow;

    public PlayerLifecycleModule(JoinPolicy joinPolicy, Component joinRejectMessage,
                                  DisconnectPolicy disconnectPolicy,
                                  PlayerReturnFlow.StatePreserver statePreserver) {
        this.joinPolicy = Objects.requireNonNull(joinPolicy, "joinPolicy");
        this.joinRejectMessage = joinRejectMessage;
        this.disconnectPolicy = Objects.requireNonNull(disconnectPolicy, "disconnectPolicy");
        this.returnFlow = new PlayerReturnFlow(statePreserver);
    }

    /**
     * Застосовує налаштовану {@link JoinPolicy} до гравця, що приєднався
     * під час активного матчу. Консюмер сам вирішує МОМЕНТ виклику (типово
     * в обробнику {@code PlayerLoggedInEvent}, лише коли
     * {@code lifecycleModule().lifecycleBus().current() != IDLE}) — цей
     * метод лише виконує саму дію.
     *
     * @return true, якщо гравцю дозволено лишитись; false — якщо відхилено ({@link JoinPolicy#REJECT}).
     */
    public boolean applyJoin(ServerPlayer player) {
        return PlayerJoinFlow.apply(player, joinPolicy, joinRejectMessage);
    }

    /**
     * Застосовує налаштовану {@link DisconnectPolicy} до гравця, що
     * повернувся після виходу. Консюмер передає {@code wasInMatch} —
     * бібліотека не зберігає власного знімка "хто був у матчі", бо це
     * дублювало б стан, який консюмер вже веде сам (список активних
     * учасників матчу).
     */
    public void applyReturn(ServerPlayer player, boolean wasInMatch) {
        returnFlow.apply(disconnectPolicy, player, wasInMatch);
    }

    /** Обгортка як {@link DisconnectHandler}, якщо консюмеру зручніше передавати функціональний інтерфейс далі. */
    public DisconnectHandler asDisconnectHandler() {
        return this::applyReturn;
    }

    public JoinPolicy joinPolicy() {
        return joinPolicy;
    }

    public DisconnectPolicy disconnectPolicy() {
        return disconnectPolicy;
    }
}
