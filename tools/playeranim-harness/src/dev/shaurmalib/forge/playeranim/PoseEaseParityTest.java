package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.core.util.Ease;
import dev.shaurmalib.common.playeranim.PoseEase;
import dev.shaurmalib.harness.T;

import java.util.HashSet;
import java.util.Set;

/** id/імена {@link PoseEase} ↔ реальний PAL {@link Ease}, і придатність кривої як fade. */
public final class PoseEaseParityTest {

    public static void run() {
        T.section("PoseEase ↔ PAL Ease (паритет)");

        for (PoseEase e : PoseEase.values()) {
            Ease pal = PoseEaseMapper.toPal(e);
            T.eq("ім'я " + e.name() + " ↔ Ease." + pal.name(), pal.name(), e.name());
        }

        // Кожен fade мусить починатись у 0 і закінчуватись у 1, інакше після fade лишається залишкова поза.
        for (PoseEase e : PoseEase.values()) {
            Ease pal = PoseEaseMapper.toPal(e);
            T.eq(e.name() + "(0)=0", pal.invoke(0f), 0.0, 1e-4);
            T.eq(e.name() + "(1)=1", pal.invoke(1f), 1.0, 1e-4);
        }

        // Що з PAL свідомо не покрито — і чому.
        Set<String> covered = new HashSet<>();
        for (PoseEase e : PoseEase.values()) covered.add(e.name());
        for (Ease pal : Ease.values()) {
            if (!covered.contains(pal.name())) {
                System.out.println("  INFO  PAL Ease." + pal.name() + " свідомо не публікується в PoseEase");
            }
        }
        T.ok("CONSTANT свідомо виключено", !covered.contains("CONSTANT"));
        T.ok("OUTBOUNCE свідомо виключено", !covered.contains("OUTBOUNCE"));
        T.eq("PAL CONSTANT(0.5)=0 (як fade — 'ніколи не з'явиться')", Ease.CONSTANT.invoke(0.5f), 0.0, 1e-6);
        // Регресійна сторожа PAL-бага: якщо його виправлять, цей рядок скаже, що виключення можна прибрати.
        T.eq("PAL-баг: OUTBOUNCE(0.5) == OUTBACK(0.5)", Ease.OUTBOUNCE.invoke(0.5f), Ease.OUTBACK.invoke(0.5f), 1e-6);
    }
}
