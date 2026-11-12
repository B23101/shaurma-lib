package dev.shaurmalib.anim.mc.emote;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.emote.Emote;
import dev.shaurmalib.anim.emote.EmoteMessage;
import dev.shaurmalib.anim.emote.EmoteNetwork;
import dev.shaurmalib.anim.emote.EmoteWheel;
import dev.shaurmalib.anim.emote.ShaurmaEmotes;
import dev.shaurmalib.anim.runtime.Animator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Клієнт емоцій: клавіша колеса, локальні слоти, запит на сервер. Клей лоадера реєструє {@link #KEY}
 * і кличе {@link #tick()} щотіка. Сам клієнт анімацію НЕ запускає — лише просить сервер, а той
 * розсилає її всім (і нам теж), тож усі бачать одне й те саме.
 */
public final class EmoteClient {

    private EmoteClient() {}

    /** Утримати — колесо; відпустити на сегменті — зіграти. */
    public static final KeyMapping KEY = new KeyMapping("key.shaurma_lib.emote_wheel", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B, "key.categories.shaurma_lib");

    private static boolean loaded;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.screen == null && canOpen()) mc.setScreen(new EmoteWheelScreen(true));
        }
    }

    public static boolean canOpen() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && !p.isSpectator() && ShaurmaEmotes.canOpenWheel(p);
    }

    /** Чи клавіша колеса зараз фізично натиснута (екран не отримує KeyMapping-подій — питаємо GLFW). */
    static boolean keyHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        InputConstants.Key k = InputConstants.getKey(KEY.saveString());
        if (k == null || k == InputConstants.UNKNOWN) return false;
        if (k.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, k.getValue()) == GLFW.GLFW_PRESS;
        if (k.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(win, k.getValue());
        return false;
    }

    // ── Слоти ────────────────────────────────────────────────────────────

    public static EmoteWheel wheel() {
        EmoteWheel w = ShaurmaEmotes.wheel();
        if (!loaded) {
            loaded = true;
            w.load(file());
        }
        // гравець ще нічого не налаштовував — колесо не порожнє, а з емоціями мода
        if (!w.configured()) w.fillEmpty(ShaurmaEmotes.available());
        return w;
    }

    public static Emote[] resolvedSlots() {
        EmoteWheel w = wheel();
        Emote[] out = new Emote[EmoteWheel.SLOTS];
        for (int i = 0; i < out.length; i++) {
            Emote e = w.resolve(i, ShaurmaEmotes.registry());
            out[i] = e != null && ShaurmaAnim.clips().has(e.clip()) ? e : null;
        }
        return out;
    }

    public static void save() {
        try {
            ShaurmaEmotes.wheel().save(file());
        } catch (IOException e) {
            LogUtils.getLogger().warn("[ShaurmaAnim] не вдалося зберегти колесо емоцій: {}", e.toString());
        }
    }

    static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("shaurma_lib").resolve("emote_wheel.txt");
    }

    // ── Запити ───────────────────────────────────────────────────────────

    /** Зіграти; якщо саме ця емоція вже грає — зупинити (повторне натискання). */
    public static void request(Emote e) {
        if (e == null) return;
        if (e.equals(activeLocal())) {
            stop();
            return;
        }
        EmoteNetwork.get().sendToServer(EmoteMessage.play(e.id()));
    }

    public static void stop() { EmoteNetwork.get().sendToServer(EmoteMessage.stop()); }

    /** Емоція, що зараз грає на локальному гравці (за кліпом в аніматорі світу), або null. */
    public static Emote activeLocal() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        Animator a = ShaurmaAnim.find(p.getUUID());
        if (a == null) return null;
        for (Emote e : ShaurmaEmotes.registry().all()) if (a.isPlaying(e.clip())) return e;
        return null;
    }

    public static void openSettings(int slot) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new EmoteSettingsScreen(slot));
    }
}
