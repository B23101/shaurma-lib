package dev.shaurmalib.forge.chat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Кнопка у вікні чату, яку реєструє КОНСЮМЕР, а бібліотека лише малює й
 * натискає.
 *
 * <p>Це саме той «API кнопок», який потрібен для кнопки налаштувань
 * (видимої лише операторам) чи майбутньої кнопки статистики: бібліотека
 * не має жодних уявлень про права чи конкретні екрани мода, тому вся
 * логіка видимості й дії лежить у самому об'єкті кнопки.</p>
 *
 * <p>Приклад:</p>
 * <pre>{@code
 * ChatScreenButtonRegistry.register(ChatScreenButton.of(
 *         "settings",
 *         () -> Component.translatable("gui.mymod.settings"),
 *         () -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2),
 *         screen -> Minecraft.getInstance().setScreen(new MySettingsScreen(screen))));
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class ChatScreenButton {

    private final String id;
    private final BooleanSupplier visible;
    private final Consumer<Screen> action;
    private final Component label;

    private ChatScreenButton(String id, Component label, BooleanSupplier visible, Consumer<Screen> action) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.visible = visible == null ? () -> true : visible;
        this.action = Objects.requireNonNull(action, "action");
    }

    public static ChatScreenButton of(String id,
                                      Component label,
                                      BooleanSupplier visible,
                                      Consumer<Screen> action) {
        return new ChatScreenButton(id, label, visible, action);
    }

    /** Кнопка без умови видимості — видима завжди. */
    public static ChatScreenButton of(String id, Component label, Consumer<Screen> action) {
        return new ChatScreenButton(id, label, null, action);
    }

    public String id() {
        return id;
    }

    public Component label() {
        return label;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    /** Викликається при кліку; аргумент — екран чату (щоб повернутись назад). */
    public void press(Screen from) {
        action.accept(from);
    }
}
