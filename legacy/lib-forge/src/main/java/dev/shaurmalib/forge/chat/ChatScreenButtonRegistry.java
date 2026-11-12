package dev.shaurmalib.forge.chat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реєстр кнопок T-екрана чату (див. {@link ChatScreenButton}) — консюмер
 * реєструє свої кнопки один раз, бібліотека малює їх у
 * {@link dev.shaurmalib.forge.client.chat.ChatHistoryScreen}.
 *
 * <p>Реєстр статичний і клієнтський: <b>уся логіка кнопок — на боці
 * консюмера</b>, бібліотека не знає ані прав, ані конкретних екранів.
 * Порядок реєстрації = порядок кнопок зліва направо.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ChatScreenButtonRegistry {

    private static final Map<String, ChatScreenButton> BUTTONS =
        Collections.synchronizedMap(new LinkedHashMap<>());

    private ChatScreenButtonRegistry() {}

    public static void register(ChatScreenButton button) {
        if (button == null) {
            throw new IllegalArgumentException("Кнопка чату не може бути null.");
        }
        BUTTONS.put(button.id(), button);
    }

    public static void unregister(String id) {
        if (id != null) BUTTONS.remove(id);
    }

    public static List<ChatScreenButton> all() {
        synchronized (BUTTONS) {
            return new ArrayList<>(BUTTONS.values());
        }
    }

    /** Знімає всі кнопки — викликати при розриві з'єднання/виході зі світу. */
    public static void clear() {
        BUTTONS.clear();
    }
}
