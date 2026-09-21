package dev.shaurmalib.common.playeranim;

import dev.shaurmalib.harness.T;
import java.util.Map;
import java.util.Optional;

public final class PoseSourceTest {

    public static void run() {
        T.section("PoseSource / PoseEase / BoneAdjustment / PoseAction / Registry");

        PoseSource s3 = new PoseSource("ns", "id", true);
        T.eq("3-арг конструктор: fadeIn=0", s3.fadeInTicks(), 0);
        T.eq("3-арг конструктор: fadeOut=0", s3.fadeOutTicks(), 0);
        T.eq("3-арг конструктор: ease=DEFAULT", s3.ease(), PoseEase.DEFAULT);
        T.eq("jsonAnimation == 3-арг", PoseSource.jsonAnimation("ns", "id", true), s3);

        T.ok("hold → looping=true", PoseSource.hold("a", "b").looping());
        T.ok("oneShot → looping=false", !PoseSource.oneShot("a", "b").looping());

        PoseSource f = PoseSource.hold("a", "b").withFade(4, 6).withEase(PoseEase.OUTCUBIC);
        T.eq("withFade in", f.fadeInTicks(), 4);
        T.eq("withFade out", f.fadeOutTicks(), 6);
        T.eq("withEase", f.ease(), PoseEase.OUTCUBIC);
        T.eq("withFadeIn зберігає out", f.withFadeIn(9).fadeOutTicks(), 6);
        T.eq("withFadeOut зберігає in", f.withFadeOut(9).fadeInTicks(), 4);
        T.eq("withFadeOut міняє out", f.withFadeOut(9).fadeOutTicks(), 9);
        T.eq("ease=null → DEFAULT", new PoseSource("a", "b", true, 0, 0, null).ease(), PoseEase.DEFAULT);

        T.ok("межа 0 дозволена", new PoseSource("a", "b", true, 0, 0, null) != null);
        T.ok("межа 40 дозволена", new PoseSource("a", "b", true, 40, 40, null) != null);
        T.throwsIae("fadeIn=41 відхиляється", () -> new PoseSource("a", "b", true, 41, 0, null));
        T.throwsIae("fadeOut=41 відхиляється", () -> new PoseSource("a", "b", true, 0, 41, null));
        T.throwsIae("fadeIn=-1 відхиляється", () -> new PoseSource("a", "b", true, -1, 0, null));
        T.throwsIae("Integer.MAX_VALUE відхиляється", () -> new PoseSource("a", "b", true, 0, Integer.MAX_VALUE, null));
        T.throwsIae("withFade поза межами відхиляється", () -> PoseSource.hold("a", "b").withFade(100, 0));

        // PoseEase
        boolean noExcluded = true;
        for (PoseEase e : PoseEase.values()) {
            if (e.name().equals("CONSTANT") || e.name().equals("OUTBOUNCE")) noExcluded = false;
        }
        T.ok("PoseEase не містить CONSTANT/OUTBOUNCE", noExcluded);
        java.util.Set<Byte> ids = new java.util.HashSet<>();
        for (PoseEase e : PoseEase.values()) ids.add(e.palId());
        T.eq("id усіх PoseEase унікальні", ids.size(), PoseEase.values().length);

        // BoneAdjustment
        BoneAdjustment.Provider byMap = BoneAdjustment.ofMap(Map.of("rightArm", BoneAdjustment.Part.pitchDegrees(90)));
        T.ok("ofMap: відома кістка", byMap.partFor("rightArm").isPresent());
        T.ok("ofMap: невідома кістка прозора", byMap.partFor("head").isEmpty());
        T.eq("pitchDegrees(90) = π/2 рад", byMap.partFor("rightArm").get().rotX(), Math.PI / 2, 1e-6);
        T.eq("Part.rotation має нульовий offset", BoneAdjustment.Part.rotation(1, 2, 3).offX(), 0.0, 0);
        T.eq("Part.offset має нульовий поворот", BoneAdjustment.Part.offset(1, 2, 3).rotY(), 0.0, 0);
        BoneAdjustment.Provider one = BoneAdjustment.ofBone("torso", BoneAdjustment.Part.NONE);
        T.ok("ofBone: збіг", one.partFor("torso").isPresent());
        T.ok("ofBone: інша кістка", one.partFor("body").isEmpty());

        // PoseAction
        PoseAction a = PoseAction.of("maniac:x", f, PoseLayerId.ITEM_ACTION);
        T.ok("PoseAction без правки", !a.hasAdjustment());
        T.ok("PoseAction withAdjustment", a.withAdjustment(byMap).hasAdjustment());
        T.throwsIae("PoseAction: порожнє ім'я", () -> PoseAction.of(" ", f, PoseLayerId.CUSTOM));
        T.throwsIae("PoseAction: null source", () -> PoseAction.of("n", null, PoseLayerId.CUSTOM));
        T.throwsIae("PoseAction: null layer", () -> PoseAction.of("n", f, null));

        // Registry
        PoseActionRegistry.clear();
        PoseActionRegistry.register(a);
        T.ok("registry: isRegistered", PoseActionRegistry.isRegistered("maniac:x"));
        T.eq("registry: get", PoseActionRegistry.get("maniac:x"), Optional.of(a));
        T.ok("registry: get(null) порожній", PoseActionRegistry.get(null).isEmpty());
        T.throwsIae("registry: дубль = IllegalStateException", () -> PoseActionRegistry.register(a));
        PoseAction b = PoseAction.of("maniac:x", f, PoseLayerId.CUSTOM);
        T.eq("registry: replace повертає попередню", PoseActionRegistry.replace(b), Optional.of(a));
        T.eq("registry: після replace нова дія", PoseActionRegistry.get("maniac:x").get().layer(), PoseLayerId.CUSTOM);
        T.eq("registry: names", PoseActionRegistry.names().size(), 1);
        PoseActionRegistry.clear();
        T.eq("registry: clear", PoseActionRegistry.names().size(), 0);

        // PoseLayerId
        T.eq("ITEM_ACTION=700", PoseLayerId.ITEM_ACTION.defaultPriority(), 700);
        T.eq("LOCOMOTION_OVERRIDE=100", PoseLayerId.LOCOMOTION_OVERRIDE.defaultPriority(), 100);
        boolean lowest = true;
        for (PoseLayerId id : PoseLayerId.values()) {
            if (id != PoseLayerId.LOCOMOTION_OVERRIDE && id.defaultPriority() <= 100) lowest = false;
        }
        T.ok("LOCOMOTION_OVERRIDE — найнижчий пріоритет", lowest);
        T.ok("ITEM_ACTION між CUSTOM і AIRPLANE_SEAT",
                PoseLayerId.ITEM_ACTION.defaultPriority() > PoseLayerId.CUSTOM.defaultPriority()
                        && PoseLayerId.ITEM_ACTION.defaultPriority() < PoseLayerId.AIRPLANE_SEAT.defaultPriority());
        java.util.Set<Integer> prios = new java.util.HashSet<>();
        for (PoseLayerId id : PoseLayerId.values()) prios.add(id.defaultPriority());
        T.eq("пріоритети шарів унікальні (порядок не залежить від порядку додавання)", prios.size(), PoseLayerId.values().length);
    }
}
