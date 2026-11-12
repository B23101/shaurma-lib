package dev.shaurmalib.anim.mc.emote;

import dev.shaurmalib.anim.emote.Emote;
import net.minecraft.network.chat.Component;

/** Тексти емоцій: переклад, якщо мод його дав, інакше назва з реєстру. */
final class EmoteText {

    private EmoteText() {}

    /** {@code emote.<ns>.<шлях>} — напр. {@code emote.maniacmod.wave}. */
    static Component name(Emote e) {
        return Component.translatableWithFallback("emote." + e.id().replace(':', '.').replace('/', '.'), e.name());
    }

    static Component category(String c) {
        return Component.translatableWithFallback("emote.category." + c, c);
    }

    static Component ui(String key, String fallback) {
        return Component.translatableWithFallback("shaurma_lib.emote." + key, fallback);
    }
}
