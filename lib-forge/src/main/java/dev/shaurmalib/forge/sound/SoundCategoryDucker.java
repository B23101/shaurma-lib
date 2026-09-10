package dev.shaurmalib.forge.sound;

import dev.shaurmalib.forge.mixin.SoundEngineAccessor;
import dev.shaurmalib.forge.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * SoundCategoryDucker (план, п. 3.5) — реально приглушує ВЖЕ активні
 * (запущені) звуки заданої {@link SoundSource}-категорії, на льоту, "на
 * рівні звукового рушія". 1:1 перенесення
 * {@code org.example.snipers_shaurma.core.client.sound.SoundCategoryDucker},
 * узагальнене для будь-якого модуля бібліотеки, що потребує ducking
 * (не лише {@code RadioAudioDucking}, а й майбутній
 * {@code CombatAreaAudioDucking}-еквівалент у консюмері).
 * <p>
 * <b>Чому не {@code SoundManager.updateSourceVolume}</b>: у ванільному
 * 1.20.1 цей виклик оновлює лише внутрішній стан категорії,
 * застосовуваний до НОВИХ звуків — уже активні канали (музика, sfx, що
 * лунали до старту ducking) лишаються на старій гучності. Рішення
 * (підглянуте в моді Dynamic FPS, SoundEngineMixin.dynamic_fps$updateVolume) —
 * напряму перебрати {@code instanceToChannel} і викликати
 * {@code channel.setVolume(...)} на кожному активному каналі категорії.
 * {@code Options}/options.txt гравця тут ніколи не чіпається — приглушення
 * відбувається виключно на живих OpenAL-каналах.
 * <p>
 * <b>PERSIST-режим</b>: {@link #setActiveVolume} приглушує лише звуки, що
 * вже грають у момент виклику. Нові звуки цієї категорії, що почнуть
 * грати ПІД ЧАС ducking, отримають повну гучність — щоб цього не було,
 * {@link #beginDuck}/{@link #endDuck} реєструють активний множник, і
 * {@link #tick()} (клієнтський тік) переприкладає його щотіку, поки
 * duck активний.
 * <p>
 * <b>Незалежні джерела duck</b>: кілька підсистем (радіо-діалог і
 * майбутня бойова зона) можуть приглушувати ту саму категорію одночасно
 * й незалежно — кожен виклик передає {@code tag}, для категорії
 * застосовується ДОБУТОК множників усіх активних тегів, тому зняття
 * одного duck-а не скидає інший.
 */
@OnlyIn(Dist.CLIENT)
public final class SoundCategoryDucker {

    private SoundCategoryDucker() {}

    /** source -> (tag -> multiplier). */
    private static final Map<SoundSource, Map<String, Float>> activeDucks = new EnumMap<>(SoundSource.class);

    /** Реєструє duck з тегом {@code tag} на категорії {@code source} і одразу застосовує сумарний множник. */
    public static void beginDuck(String tag, SoundSource source, float multiplier) {
        activeDucks.computeIfAbsent(source, s -> new java.util.HashMap<>()).put(tag, multiplier);
        setActiveVolume(source, combinedMultiplier(source));
    }

    /** Знімає duck із тегом {@code tag} з категорії {@code source} і перезастосовує рештку активних множників. */
    public static void endDuck(String tag, SoundSource source) {
        Map<String, Float> byTag = activeDucks.get(source);
        if (byTag != null) {
            byTag.remove(tag);
            if (byTag.isEmpty()) activeDucks.remove(source);
        }
        setActiveVolume(source, combinedMultiplier(source));
    }

    /** Знімає duck із тегом {@code tag} з УСІХ категорій одночасно — зручно на "стоп все". */
    public static void endDuckAll(String tag) {
        for (SoundSource source : SoundSource.values()) {
            endDuck(tag, source);
        }
    }

    /**
     * Поточний сумарний duck-множник категорії — використовується
     * mixin-хуками, що застосовують ducking одразу в момент старту
     * звуку, не чекаючи наступного {@link #tick()}. Повертає 1.0f, якщо
     * активних duck-ів немає.
     */
    public static float currentMultiplier(SoundSource source) {
        return combinedMultiplier(source);
    }

    private static float combinedMultiplier(SoundSource source) {
        Map<String, Float> byTag = activeDucks.get(source);
        if (byTag == null || byTag.isEmpty()) return 1.0f;
        float result = 1.0f;
        for (float m : byTag.values()) result *= m;
        return result;
    }

    /**
     * Викликати з клієнтського {@code ClientTickEvent} (END-фаза).
     * Переприкладає всі активні duck-множники, щоб приглушувались і
     * звуки, що з'явились щойно (після початкового {@link #beginDuck}).
     * <p>
     * Автоматично підписано на реальну подію через {@link TickListener}
     * нижче — модулю, що використовує ducking (напр. {@code RadioAudioDucking}),
     * не потрібно самому підписуватись на {@code ClientTickEvent}.
     */
    public static void tick() {
        if (activeDucks.isEmpty()) return;
        for (SoundSource source : activeDucks.keySet()) {
            setActiveVolume(source, combinedMultiplier(source));
        }
    }

    @Mod.EventBusSubscriber(modid = "shaurma_lib", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    private static final class TickListener {
        @SubscribeEvent
        static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            SoundCategoryDucker.tick();
        }
    }

    /**
     * Множить гучність усіх активних звуків категорії {@code source} на
     * {@code multiplier} (0..1) "на льоту". Для {@code SoundSource.MASTER}
     * множник застосовується через {@code Listener.setGain(...)} —
     * глобальний OpenAL listener gain, миттєво приглушує геть усе.
     */
    public static void setActiveVolume(SoundSource source, float multiplier) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        SoundEngine engine = getEngine(mc);
        if (engine == null) return;
        SoundEngineAccessor accessor = (SoundEngineAccessor) engine;

        if (source == SoundSource.MASTER) {
            try {
                float baseGain = mc.options.getSoundSourceVolume(SoundSource.MASTER);
                accessor.shaurmaLib$getListener().setGain(baseGain * multiplier);
            } catch (Exception ignored) {}
            return;
        }

        Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
        try {
            instanceToChannel = accessor.shaurmaLib$getInstanceToChannel();
        } catch (Exception e) {
            return;
        }
        if (instanceToChannel == null) return;

        // Копія — instanceToChannel може мутуватись паралельно, поки звук зупиняється.
        List<SoundInstance> sounds;
        try {
            sounds = new ArrayList<>(instanceToChannel.keySet());
        } catch (Throwable t) {
            return;
        }

        for (SoundInstance instance : sounds) {
            if (instance.getSource() != source) continue;
            ChannelAccess.ChannelHandle handle = instanceToChannel.get(instance);
            if (handle == null) continue;

            float baseVolume;
            try {
                baseVolume = accessor.shaurmaLib$calculateVolume(instance);
            } catch (Exception e) {
                continue;
            }
            float target = baseVolume * multiplier;
            handle.execute(channel -> channel.setVolume(target));
        }
    }

    private static SoundEngine getEngine(Minecraft mc) {
        try {
            return ((SoundManagerAccessor) mc.getSoundManager()).shaurmaLib$getSoundEngine();
        } catch (Throwable t) {
            return null;
        }
    }
}
