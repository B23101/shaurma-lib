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
        System.out.println(failed == 0 ? "\nPLUGIN EXPORT OK" : "\nPLUGIN EXPORT FAILED: " + failed);
        if (failed > 0) System.exit(1);
    }

    static int check(boolean ok, String name) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        return ok ? 0 : 1;
    }
}
