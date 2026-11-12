package dev.shaurmalib.harness;

/** Мінімальний тест-хелпер: без JUnit (Maven Central у CI-пісочниці недоступний). */
public final class T {
    public static int pass = 0, fail = 0;

    private T() {}

    public static void ok(String name, boolean cond) {
        if (cond) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name);
        }
    }

    public static void eq(String name, double actual, double expected, double eps) {
        boolean c = Math.abs(actual - expected) <= eps;
        if (!c) name += "  (actual=" + actual + ", expected=" + expected + ")";
        ok(name, c);
    }

    public static void eq(String name, Object actual, Object expected) {
        boolean c = java.util.Objects.equals(actual, expected);
        if (!c) name += "  (actual=" + actual + ", expected=" + expected + ")";
        ok(name, c);
    }

    public static void throwsIae(String name, Runnable r) {
        try {
            r.run();
            ok(name + " (очікувався виняток)", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ok(name, true);
        }
    }

    public static void section(String s) {
        System.out.println("\n== " + s);
    }
}
