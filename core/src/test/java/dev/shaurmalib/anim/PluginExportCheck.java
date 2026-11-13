package dev.shaurmalib.anim;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.io.AnimResourceLoader;
import dev.shaurmalib.anim.mesh.GeoMesh;
import dev.shaurmalib.anim.mesh.PropBaker;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.Rig;
import dev.shaurmalib.anim.runtime.Animator;
import dev.shaurmalib.anim.runtime.ClipLibrary;
import dev.shaurmalib.anim.runtime.PlayRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Читає вихід Blockbench-плагіна ТИМ САМИМ рушієм, що й гра: формат має збігатися в обидва боки. */
public final class PluginExportCheck {

    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]).resolve("assets");
        List<String> warn = new ArrayList<>();
        AnimResourceLoader rl = new AnimResourceLoader(warn::add);
        try (Stream<Path> files = Files.walk(assets)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                Path rel = assets.relativize(p);
                String path = rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/');
                if (AnimResourceLoader.accepts(path)) rl.accept(rel.getName(0).toString(), path, Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        int failed = 0;
        failed += check(warn.isEmpty(), "без попереджень " + warn);
        AnimationClip c = rl.clips().get("demo:medkit_use");
        failed += check(c != null && c.props().size() == 1 && c.events().size() == 3, "кліп medkit_use з пропсом і подіями");
        GeoMesh mesh = rl.meshes().get("demo:props/medkit");
        failed += check(mesh != null && mesh.bones.size() == 2, "геометрія пропса");
        ClipLibrary lib = new ClipLibrary();
        rl.clips().values().forEach(lib::register);
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.play(PlayRequest.of("demo:medkit_use"));
        for (int i = 0; i < 12; i++) a.tick(null, null, null);
        Pose out = new Pose(Rig.PLAYER);
        a.evaluate(0.5f, null, true, out);
        failed += check(out.touched("right_arm") && out.bone("right_arm").rx < -30 && !out.touched("left_leg"), "поза: рука піднята, ноги не чіпає (маска)");
        failed += check(out.props().size() == 1 && out.hideRightHeld, "пропс активний, ванільний предмет сховано");
        float[] lidMinY = { 9 };
        PropBaker.bake(mesh, Rig.PLAYER.pivot(Rig.PLAYER.indexOf("right_item")),
            (bone, type, o) -> a.samplePropBone(c, bone, type, 0.6f, o),
            (pos, uv, nx, ny, nz) -> { for (int v = 0; v < 4; v++) lidMinY[0] = Math.min(lidMinY[0], pos[v * 3 + 1]); });
        failed += check(lidMinY[0] < 0, "пропс запікається з анімацією кришки");

        // імпорт моделі в Blockbench (.geo.json → сцена → знову geo) дає ту саму геометрію й UV
        GeoMesh re = rl.meshes().get("demo:props/medkit_reimport");
        failed += check(re != null && quads(re).equals(quads(mesh)), "імпортована й переекспортована модель = оригінал (вершини + UV)");

        // вільний пропс: parent world, напівпрозорий, анімується власним треком (кирилична назва групи)
        AnimationClip aura = rl.clips().get("demo:aura_pulse");
        dev.shaurmalib.anim.clip.PropSpec ap = aura == null || aura.props().isEmpty() ? null : aura.props().get(0);
        failed += check(ap != null && ap.isWorld() && ap.translucent() && ap.emissive() && "demo:props/aura".equals(ap.model()),
            "вільний пропс world, напівпрозорий");
        float[] s = new float[3];
        failed += check(ap != null && a.samplePropBone(aura, "Аура", dev.shaurmalib.anim.clip.BoneTrack.ChannelType.SCALE, 0.5f, s)
            && Math.abs(s[0] - 1.5f) < 1e-3, "трек пропса знаходиться за іменем групи з geo");
        GeoMesh am = rl.meshes().get("demo:props/aura");
        float[] minY = { 99 };
        if (am != null) PropBaker.bake(am, new float[3], null, (pos, uv, nx, ny, nz) -> { for (int v = 0; v < 4; v++) minY[0] = Math.min(minY[0], pos[v * 3 + 1]); });
        failed += check(am != null && Math.abs(minY[0]) < 1e-4, "вільний пропс у просторі ступнів (y=0 — земля)");
        // стан тримання з файлу: без коду, правило з'являється в бібліотеці
        AnimationClip hold = rl.clips().get("demo:fuel_hold");
        failed += check(hold != null && hold.hold() != null && hold.loop() == AnimationClip.LoopMode.HOLD
            && lib.holdBindings().size() == 1, "стан тримання з файлу → правило");
        Animator h = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        dev.shaurmalib.anim.runtime.AnimSubject subj = new dev.shaurmalib.anim.runtime.AnimSubject() {
            public UUID uuid() { return h.id(); }
            public Object entity() { return null; }
            public dev.shaurmalib.anim.runtime.AnimInputs inputs() { return h.inputs(); }
            public Animator animator() { return h; }
        };
        // «Ванільна» база: те, що дає рух гравця (змах руки, кроки ніг)
        Pose base = new Pose(Rig.PLAYER);
        java.util.function.BiConsumer<Float, Float> setBase = (arm, legs) -> {
            base.reset();
            base.bone("right_arm").rx = arm;
            base.bone("left_arm").rx = -arm;
            base.bone("right_leg").rx = legs;
            base.bone("left_leg").rx = -legs;
        };
        Pose hp = new Pose(Rig.PLAYER);
        java.util.function.IntConsumer run = n -> { for (int i = 0; i < n; i++) h.tick(subj, lib.holdBindings(), null); };
        setBase.accept(5f, 0f);
        run.accept(10);
        h.evaluate(1f, base, true, hp);
        failed += check(!hp.touched("right_arm"), "стан: без предмета — не грає");
        h.inputs().mainHandItem = "maniacmod:fuel_canister";
        run.accept(10);
        Pose pure = new Pose(Rig.PLAYER);
        h.evaluate(1f, null, true, pure);
        float P = pure.bone("right_arm").rx; // поза з Blockbench
        h.evaluate(1f, base, true, hp);
        failed += check(Math.abs(P) > 20 && near(hp.bone("right_arm").rx, P + 5f) && near(hp.bone("left_arm").rx, -5f) && hp.stateRightArm,
            "стан: стоїть — поза + дихання idle (×1), ліва рука вільна, адаптеру сказано прибрати ванільну позу предмета");
        h.inputs().limbSwingAmount = 1f; // біг
        setBase.accept(30f, 40f);
        run.accept(3);
        h.evaluate(1f, base, true, hp);
        failed += check(near(hp.bone("right_arm").rx, P + 0.5f * 30f) && near(hp.bone("right_leg").rx, 40f) && near(hp.bone("left_arm").rx, -30f),
            "стан: біг — поза + 0.5 змаху, ноги й ліва рука біжать");
        h.inputs().limbSwingAmount = 0f;
        h.inputs().sneaking = true; // шифт: ванільний нахил рук +23°
        setBase.accept(23f, 0f);
        h.evaluate(1f, base, true, hp);
        failed += check(near(hp.bone("right_arm").rx, P + 23f), "стан: шифт — рука нахиляється разом з тілом (×1)");
        h.inputs().sneaking = false;
        setBase.accept(-80f, 0f); // ванільний змах удару
        h.inputs().swinging = true;
        run.accept(6);
        h.evaluate(1f, base, true, hp);
        failed += check(Math.abs(hp.bone("right_arm").rx - (-80f)) < 8f, "стан: удар — ванільний змах руки з каністрою");
        h.inputs().swinging = false;
        h.inputs().mainHandItem = "minecraft:air";
        setBase.accept(5f, 0f);
        run.accept(20);
        h.evaluate(1f, base, true, hp);
        failed += check(near(hp.bone("right_arm").rx, 5f) && !hp.stateRightArm, "стан: прибрав предмет — згасло, ванільна рука");

        // синхронна: аптечка на ходу — ноги й голова лишаються руху, навіть якщо в кліпі є ключ ноги
        Animator sy = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        sy.play(PlayRequest.of("demo:medkit_walk"));
        for (int i = 0; i < 6; i++) sy.tick(null, null, null);
        setBase.accept(30f, 40f);
        sy.evaluate(0.5f, null, true, pure);
        sy.evaluate(0.5f, base, true, hp);
        failed += check(near(hp.bone("right_leg").rx, 40f) && near(hp.bone("right_arm").rx, pure.bone("right_arm").rx)
            && Math.abs(pure.bone("right_arm").rx) > 30,
            "синхронна: ноги йдуть (ключ ноги в кліпі ігнорується маскою), руку веде аптечка");

        // повна: усе інше вимкнено — ноги в спокої, хоч гравець біжить
        Animator fu = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        fu.play(PlayRequest.of("demo:lie"));
        for (int i = 0; i < 10; i++) fu.tick(null, null, null);
        fu.evaluate(0.5f, base, true, hp);
        failed += check(near(hp.bone("right_leg").rx, 0f) && near(hp.bone("left_arm").rx, 0f), "повна: ноги й руки без ключів стоять у спокої");
        // «кинув і працює»: той самий експорт архівом (з текою-обгорткою) і текою в <гра>/shaurma_anim/
        Path game = Files.createTempDirectory("shaurma_game");
        Path packDir = game.resolve(dev.shaurmalib.anim.io.AnimPackScanner.FOLDER);
        Files.createDirectories(packDir);
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(packDir.resolve("medkit_resources.zip")));
             Stream<Path> all = Files.walk(assets)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            for (Path p : (Iterable<Path>) all.filter(Files::isRegularFile)::iterator) {
                zos.putNextEntry(new java.util.zip.ZipEntry("wrapper/assets/" + assets.relativize(p).toString().replace('\\', '/')));
                zos.write(Files.readAllBytes(p));
                zos.closeEntry();
            }
        }
        Path loose = packDir.resolve("aura_folder").resolve("assets").resolve("demo2").resolve("shaurma_anim").resolve("player");
        Files.createDirectories(loose);
        Files.copy(assets.resolve("demo/shaurma_anim/player/aura.json"), loose.resolve("aura.json"));
        List<String> warn2 = new ArrayList<>();
        AnimResourceLoader rl2 = new AnimResourceLoader(warn2::add);
        dev.shaurmalib.anim.io.AnimPackScanner.Result res = dev.shaurmalib.anim.io.AnimPackScanner.scan(packDir, rl2, warn2::add);
        failed += check(warn2.isEmpty() && rl2.clips().containsKey("demo:medkit_use") && rl2.clips().containsKey("demo:fuel_hold")
            && rl2.meshes().containsKey("demo:props/medkit") && rl2.clips().containsKey("demo2:aura_pulse"),
            "папка гри: .zip (без розпаковки, з текою-обгорткою) і тека — кліпи й моделі " + warn2);
        failed += check(res.textures().containsKey("demo:textures/shaurma_anim/medkit.png") && res.sources().size() == 2,
            "папка гри: PNG пропсів для реєстрації як текстур " + res.sources());
        List<String> warn3 = new ArrayList<>();
        dev.shaurmalib.anim.io.AnimPackScanner.scan(game.resolve("nope"), new AnimResourceLoader(warn3::add), warn3::add);
        failed += check(Files.isDirectory(game.resolve("nope")) && warn3.isEmpty(), "немає папки — створюється порожня, без помилок");
        System.out.println(failed == 0 ? "\nPLUGIN EXPORT OK" : "\nPLUGIN EXPORT FAILED: " + failed);
        if (failed > 0) System.exit(1);
    }

    /** Квади моделі як відсортований набір рядків (позиції + UV, округлені) — порядок не важливий. */
    static List<String> quads(GeoMesh m) {
        List<String> out = new ArrayList<>();
        PropBaker.bake(m, new float[3], null, (pos, uv, nx, ny, nz) -> {
            StringBuilder b = new StringBuilder();
            List<String> verts = new ArrayList<>();
            for (int v = 0; v < 4; v++) {
                verts.add(String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f@%.4f,%.4f",
                    pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2], uv[v * 2], uv[v * 2 + 1]));
            }
            java.util.Collections.sort(verts);
            b.append(String.join(" ", verts));
            out.add(b.toString());
        });
        java.util.Collections.sort(out);
        return out;
    }

    static boolean near(float a, float b) { return Math.abs(a - b) < 0.5f; }

    static int check(boolean ok, String name) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        return ok ? 0 : 1;
    }
}
