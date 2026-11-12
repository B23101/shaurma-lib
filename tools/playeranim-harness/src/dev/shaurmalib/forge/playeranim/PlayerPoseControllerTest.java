package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.shaurmalib.common.playeranim.BoneAdjustment;
import dev.shaurmalib.common.playeranim.PoseAction;
import dev.shaurmalib.common.playeranim.PoseActionRegistry;
import dev.shaurmalib.common.playeranim.PoseEase;
import dev.shaurmalib.common.playeranim.PoseLayerId;
import dev.shaurmalib.common.playeranim.PoseSource;
import dev.shaurmalib.harness.T;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Інтеграційний тест {@link PlayerPoseController} — з заглушками для класів
 * Minecraft (гравець, {@code PlayerAnimationAccess}, реєстр анімацій), але зі
 * справжнім PAL і справжнім рантаймом. Перевіряє те, що не бачить тест
 * одного шару: relog-перенос, паузу, витік записів, іменовані дії, пріоритети.
 */
public final class PlayerPoseControllerTest {

    private static float rot(AbstractClientPlayer p, String bone) {
        return p.stack.get3DTransform(bone, TransformType.ROTATION, 0f, Vec3f.ZERO).getX();
    }

    private static void tick(AbstractClientPlayer p, int n) {
        for (int i = 0; i < n; i++) {
            p.stack.tick();
            PlayerPoseController.tickAll();
        }
    }

    private static void reg(String id, int begin, int end, boolean loop, float v) {
        PlayerAnimationRegistry.M.put(new ResourceLocation("t", id), Clips.arm(begin, end, loop, v));
    }

    public static void run() {
        T.section("PlayerPoseController (інтеграція, заглушки MC + справжній PAL)");
        PlayerAnimationRegistry.M.clear();
        PlayerPoseController.invalidateAnimationCache();
        reg("hold", 0, 10, true, 1.0f);
        reg("shot", 0, 10, false, 1.0f);
        reg("other", 0, 10, true, 2.0f);

        AbstractClientPlayer p = new AbstractClientPlayer(UUID.randomUUID());
        PoseSource hold = PoseSource.hold("t", "hold").withEase(PoseEase.LINEAR).withFade(4, 4);

        T.ok("невідомий ресурс → false (консюмер ретраїть)",
                !PlayerPoseController.trigger(p, PoseLayerId.ITEM_ACTION, PoseSource.hold("t", "нема")));
        T.ok("null source → false", !PlayerPoseController.trigger(p, PoseLayerId.ITEM_ACTION, null));
        T.ok("isActive до запуску = false", !PlayerPoseController.isActive(p, PoseLayerId.ITEM_ACTION));

        T.ok("trigger → true", PlayerPoseController.trigger(p, PoseLayerId.ITEM_ACTION, hold));
        T.ok("isActive", PlayerPoseController.isActive(p, PoseLayerId.ITEM_ACTION));
        tick(p, 6);
        T.eq("поза повна після fade-in", rot(p, "rightArm"), 1.0, 1e-4);

        PlayerPoseController.stop(p, PoseLayerId.ITEM_ACTION);
        T.ok("під час fade-out isActive=false (можна запускати наступну)", !PlayerPoseController.isActive(p, PoseLayerId.ITEM_ACTION));
        T.ok("…але isVisible=true", PlayerPoseController.isVisible(p, PoseLayerId.ITEM_ACTION));
        tick(p, 4);
        T.ok("після fade-out isVisible=false", !PlayerPoseController.isVisible(p, PoseLayerId.ITEM_ACTION));
        T.eq("rot=0", rot(p, "rightArm"), 0.0, 1e-6);
        T.ok("стек неактивний", !p.stack.isActive());

        // ── one-shot сам завершується (без жодного stop від консюмера)
        PlayerPoseController.trigger(p, PoseLayerId.ITEM_ACTION,
                PoseSource.oneShot("t", "shot").withEase(PoseEase.LINEAR).withFadeOut(3));
        tick(p, 13);
        T.ok("one-shot завершився сам за 13 тіків", !PlayerPoseController.isVisible(p, PoseLayerId.ITEM_ACTION));
        T.ok("стек неактивний", !p.stack.isActive());

        // ── пауза: PAL стоїть → наш лічильник теж стоїть
        PlayerPoseController.trigger(p, PoseLayerId.ITEM_ACTION,
                PoseSource.oneShot("t", "shot").withEase(PoseEase.LINEAR).withFadeOut(3));
        tick(p, 5);
        Minecraft.PAUSED = true;
        for (int i = 0; i < 50; i++) PlayerPoseController.tickAll(); // PAL не тікає (гра на паузі)
        Minecraft.PAUSED = false;
        T.ok("50 тіків паузи не з'їли one-shot", PlayerPoseController.isVisible(p, PoseLayerId.ITEM_ACTION));
        tick(p, 8);
        T.ok("після паузи дограє й завершується", !PlayerPoseController.isVisible(p, PoseLayerId.ITEM_ACTION));

        // ── relog: новий екземпляр гравця (той самий UUID), поза посеред дії
        AbstractClientPlayer old = new AbstractClientPlayer(UUID.randomUUID());
        UUID id = old.getUUID();
        PlayerPoseController.trigger(old, PoseLayerId.ITEM_ACTION, hold);
        tick(old, 6);
        AbstractClientPlayer fresh = new AbstractClientPlayer(id);
        T.ok("новий екземпляр: запис не бачить чужої пози", !PlayerPoseController.isActive(fresh, PoseLayerId.ITEM_ACTION));
        T.ok("trigger на новому екземплярі → true", PlayerPoseController.trigger(fresh, PoseLayerId.ITEM_ACTION, PoseSource.hold("t", "other")));
        T.ok("поза одразу видна на НОВОМУ стеку", rot(fresh, "rightArm") > 0.5f);
        tick(fresh, 6);
        T.eq("…рівно 2.0 (не 1.0 зі старого запису)", rot(fresh, "rightArm"), 2.0, 1e-4);
        T.ok("старий стек звільнено (шар знято)", !old.stack.isActive());
        PlayerPoseController.cleanup(id);
        T.ok("cleanup прибрав записи", !PlayerPoseController.isVisible(fresh, PoseLayerId.ITEM_ACTION));

        // ── жорстка зупинка стирає і правку кісток
        AbstractClientPlayer h = new AbstractClientPlayer(UUID.randomUUID());
        BoneAdjustment.Provider adj = BoneAdjustment.ofBone("head", BoneAdjustment.Part.rotation(1f, 0, 0));
        T.ok("applyBoneAdjustment → true", PlayerPoseController.applyBoneAdjustment(h, PoseLayerId.ITEM_ACTION, adj));
        PlayerPoseController.trigger(h, PoseLayerId.ITEM_ACTION, hold.withFade(0, 0));
        tick(h, 2);
        T.eq("правка head діє", rot(h, "head"), 1.0, 1e-4);
        PlayerPoseController.stopAll(h, true);
        T.eq("hard stopAll: одразу 0", rot(h, "head"), 0.0, 1e-6);
        T.eq("hard stopAll: rightArm=0", rot(h, "rightArm"), 0.0, 1e-6);
        PlayerPoseController.trigger(h, PoseLayerId.ITEM_ACTION, hold.withFade(0, 0));
        T.eq("після hard stop правка не просочилась у нову позу", rot(h, "head"), 0.0, 1e-6);
        PlayerPoseController.clearBoneAdjustment(h, PoseLayerId.ITEM_ACTION);
        PlayerPoseController.stopAll(h, true);

        // ── іменовані дії
        PoseActionRegistry.clear();
        PoseActionRegistry.register(PoseAction.of("maniac:repair", PoseSource.hold("t", "hold").withEase(PoseEase.LINEAR).withFade(2, 2), PoseLayerId.ITEM_ACTION)
                .withAdjustment(BoneAdjustment.ofBone("head", BoneAdjustment.Part.rotation(0.5f, 0, 0))));
        AbstractClientPlayer a = new AbstractClientPlayer(UUID.randomUUID());
        T.ok("playAction невідомої дії → false", !PlayerPoseController.playAction(a, "maniac:нема"));
        T.ok("playAction → true", PlayerPoseController.playAction(a, "maniac:repair"));
        tick(a, 4);
        T.eq("дія: поза (rightArm=1.0)", rot(a, "rightArm"), 1.0, 1e-4);
        T.eq("дія: правка (head=0.5)", rot(a, "head"), 0.5, 1e-4);
        PlayerPoseController.stopAction(a, "maniac:repair");
        tick(a, 2);
        T.ok("stopAction → згасла", !PlayerPoseController.isVisible(a, PoseLayerId.ITEM_ACTION));
        T.eq("після дії head=0 (правка не лишилась)", rot(a, "head"), 0.0, 1e-6);

        // ── пріоритети шарів у справжньому стеку гравця
        AbstractClientPlayer m = new AbstractClientPlayer(UUID.randomUUID());
        PlayerPoseController.trigger(m, PoseLayerId.LOCOMOTION_OVERRIDE, PoseSource.hold("t", "hold"));
        T.eq("LOCOMOTION_OVERRIDE діє сам", rot(m, "rightArm"), 1.0, 1e-4);
        PlayerPoseController.trigger(m, PoseLayerId.ITEM_ACTION, PoseSource.hold("t", "other"));
        T.eq("ITEM_ACTION перекриває локомоцію (не сума)", rot(m, "rightArm"), 2.0, 1e-4);
        PlayerPoseController.stop(m, PoseLayerId.ITEM_ACTION);
        T.eq("зняли ITEM_ACTION → знов локомоція", rot(m, "rightArm"), 1.0, 1e-4);
        PlayerPoseController.stopAll(m, true);

        // ── камера: опційна, глобальний вимикач
        PlayerAnimationRegistry.M.put(new ResourceLocation("t", "headtilt"), Clips.head(0, 10, true, 0.5f));
        AbstractClientPlayer c = new AbstractClientPlayer(UUID.randomUUID());
        PlayerPoseController.trigger(c, PoseLayerId.ITEM_ACTION, PoseSource.hold("t", "headtilt"));
        T.ok("камера: без withCameraFollow зсуву нема", PlayerPoseController.cameraOffset(c, 0f) == null);
        PlayerPoseController.trigger(c, PoseLayerId.ITEM_ACTION, PoseSource.hold("t", "headtilt").withCameraFollow(1f));
        float[] off = PlayerPoseController.cameraOffset(c, 0f);
        T.ok("камера: withCameraFollow(1) → зсув є", off != null);
        T.eq("камера: pitch=0.5", off[0], 0.5, 1e-5);
        PlayerPoseController.setCameraFollowEnabled(false);
        T.ok("глобальний вимикач → зсуву нема", PlayerPoseController.cameraOffset(c, 0f) == null);
        PlayerPoseController.setCameraFollowEnabled(true);
        T.ok("вимикач знову ввімкнено → зсув повернувся", PlayerPoseController.cameraOffset(c, 0f) != null);

        // два шари разом — зсуви сумуються
        PlayerPoseController.trigger(c, PoseLayerId.CUSTOM, PoseSource.hold("t", "headtilt").withCameraFollow(0.5f));
        T.eq("два шари: 0.5 + 0.25 = 0.75", PlayerPoseController.cameraOffset(c, 0f)[0], 0.75, 1e-5);
        PlayerPoseController.stopAll(c, true);
        T.ok("після hard stopAll зсуву нема", PlayerPoseController.cameraOffset(c, 0f) == null);
        T.ok("гравець без записів → null", PlayerPoseController.cameraOffset(new AbstractClientPlayer(UUID.randomUUID()), 0f) == null);

        // ── витік: гравці, що зникли без cleanup, не накопичуються
        for (int i = 0; i < 200; i++) {
            AbstractClientPlayer ghost = new AbstractClientPlayer(UUID.randomUUID());
            PlayerPoseController.trigger(ghost, PoseLayerId.ITEM_ACTION, hold);
        }
        System.gc();
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        System.gc();
        PlayerPoseController.tickAll();
        int leftover = PlayerPoseController.debugTrackedPlayers();
        T.ok("200 «привидів» після GC + tickAll не тримаються в мапі (лишилось " + leftover + ")", leftover < 50);
    }
}
