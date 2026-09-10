package dev.shaurmalib.common.chat;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Клієнтська історія чату за поточний раунд (план, п. 3.9 + 3.29) —
 * перенесення {@code ChatHistoryStore} snipers_shaurma, з ОДНІЄЮ
 * архітектурною зміною: оригінал був повністю статичним класом
 * (єдина історія на весь клієнт), тут — звичайний інстанс-клас, щоб
 * бібліотека могла тримати окремі історії для різних consumer-модів
 * одночасно (напр. якщо колись в одному клієнті активні кілька
 * лібових чат-каналів). {@link dev.shaurmalib.forge.chat.ChatModule}
 * (lib-forge) тримає єдиний статичний інстанс на споживача — так само
 * зручно, як і раніше, для типового випадку одного мода.
 * <p>
 * Поєднує:
 *  - death-нотифікації (ті самі записи, що спливають у kill-feed
 *    {@code AlertNotificationSystem});
 *  - звичайні чат-повідомлення (глобальні й командні);
 *  - системні повідомлення (вивід команд, тощо, перехоплені міксином
 *    ванільного {@code ChatComponent}).
 * <p>
 * Очищається на старті кожного нового раунду — консюмер викликає
 * {@link #reset()} з тієї ж точки, де скидається інша ігрова клієнтська
 * статистика (у snipers це був виклик поруч з countdown/game-start).
 */
public final class ChatHistoryStore {

    private static final int MAX_ENTRIES = 500; // запобіжник від необмеженого росту при довгих раундах

    private final List<ChatEntry> history = new ArrayList<>();

    public synchronized void addDeath(Component message) {
        history.add(new ChatEntry(ChatEntryType.DEATH, message, null, null));
        trim();
    }

    public synchronized void addChat(Component message, UUID senderUuid, String senderTeamId, boolean isTeam) {
        history.add(new ChatEntry(isTeam ? ChatEntryType.CHAT_TEAM : ChatEntryType.CHAT_GLOBAL,
                message, senderUuid, senderTeamId));
        trim();
    }

    public synchronized void addSystem(Component message) {
        history.add(new ChatEntry(ChatEntryType.SYSTEM, message, null, null));
        trim();
    }

    private void trim() {
        while (history.size() > MAX_ENTRIES) {
            history.remove(0);
        }
    }

    /** Копія поточної історії (снапшот, безпечний для ітерації поза синхронізацією). */
    public synchronized List<ChatEntry> snapshot() {
        return new ArrayList<>(history);
    }

    /** Очищає історію — викликати на старті кожного нового раунду. */
    public synchronized void reset() {
        history.clear();
    }
}
