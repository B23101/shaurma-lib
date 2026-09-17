package dev.shaurmalib.forge;

import dev.shaurmalib.forge.network.ShaurmaLibNetwork;
import dev.shaurmalib.forge.overlay.IntroOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

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

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "shaurma_lib");
    public static final RegistryObject<SoundEvent> INTRO_MUSIC = SOUND_EVENTS.register(
            "intro_music",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("shaurma_lib", "intro_music")));

    /**
     * Звук вхідного чат-повідомлення ({@code ChatModule.SOUND_ID}).
     * Реєструється бібліотекою, щоб консюмеру не треба було дублювати
     * реєстр звуків у себе: йому лишається покласти {@code chat_message.ogg}
     * у {@code assets/shaurma_lib/sounds/} (або перекрити власним файлом
     * з тим самим id).
     */
    public static final RegistryObject<SoundEvent> CHAT_MESSAGE = SOUND_EVENTS.register(
            "chat_message",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("shaurma_lib", "chat_message")));

    public ShaurmaLibMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUND_EVENTS.register(modEventBus);
        // Стабільний мережевий канал lib (packet IDs фіксовані між релізами).
        ShaurmaLibNetwork.register();

        // Базове intro вмикається автоматично для всіх споживачів бібліотеки.
        // Головний мод може повторно викликати IntroOverlay.attach(...) зі
        // своїм enabled-supplier, звуком і текстом або передати () -> false.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IntroOverlay.attach(
                () -> true, () -> null, () -> 1.0, "ШАУРМА", ""));
    }
}