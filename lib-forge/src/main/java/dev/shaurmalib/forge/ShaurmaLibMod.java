package dev.shaurmalib.forge;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import net.minecraftforge.fml.common.Mod;

/**
 * Точка входу Forge-мода бібліотеки (modId {@code shaurma_lib}, задекларований
 * у {@code META-INF/mods.toml}).
 * <p>
 * <b>Обов'язкова для завантаження jar-а в /mods:</b> Forge 1.20.1 вимагає, щоб
 * кожен modId з {@code mods.toml} (modLoader {@code javafml}) мав рівно один
 * клас з анотацією {@code @Mod("shaurma_lib")} — без нього FML кидає помилку
 * "missing @Mod annotation" при старті і гра не завантажує жодного мода.
 * <p>
 * Конструктор реєструє власний мережевий канал бібліотеки
 * ({@link ShaurmaLibNetwork#register()}, namespace {@code shaurma_lib}) —
 * окремий канал від каналів модів-споживачів (план, розділ 6, п. 2).
 * <p>
 * <b>Нічого більше тут не активується.</b> Усі модулі бібліотеки вмикаються
 * виключно через {@code ShaurmaLib.init(consumerModId, modEventBus).withXxx(...)}
 * у конструкторі {@code @Mod}-класу самого споживача (snipers_shaurma,
 * maniac-mode). Цей клас не робить жодних викликів {@code withXxx(...)} —
 * тому просто покласти lib-forge в /mods поруч із немігрованим модом не
 * змінює жодної поведінки споживача.
 */
@Mod("shaurma_lib")
public final class ShaurmaLibMod {

    public ShaurmaLibMod() {
        // Стабільний мережевий канал lib (packet IDs фіксовані між релізами).
        ShaurmaLibNetwork.register();
    }
}