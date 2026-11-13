package dev.shaurmalib.anim;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.clip.ClipEvent;
import dev.shaurmalib.anim.emote.Emote;
import dev.shaurmalib.anim.emote.EmoteMessage;
import dev.shaurmalib.anim.emote.EmoteRegistry;
import dev.shaurmalib.anim.emote.EmoteSessions;
import dev.shaurmalib.anim.emote.EmoteWheel;
import dev.shaurmalib.anim.emote.ShaurmaEmotes;
import dev.shaurmalib.anim.io.AnimResourceLoader;
import dev.shaurmalib.anim.io.ClipParser;
import dev.shaurmalib.anim.io.Json;
import dev.shaurmalib.anim.math.Easing;
import dev.shaurmalib.anim.mesh.GeoMesh;
import dev.shaurmalib.anim.mesh.PropBaker;
import dev.shaurmalib.anim.molang.Molang;
import dev.shaurmalib.anim.molang.MolangContext;
import dev.shaurmalib.anim.net.AnimMessage;
import dev.shaurmalib.anim.pose.BoneTransform;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.BoneMask;
import dev.shaurmalib.anim.rig.Rig;
import dev.shaurmalib.anim.runtime.AnimBinding;
import dev.shaurmalib.anim.runtime.AnimInputs;
import dev.shaurmalib.anim.runtime.AnimSubject;
import dev.shaurmalib.anim.runtime.Animator;
import dev.shaurmalib.anim.runtime.ClipLibrary;
import dev.shaurmalib.anim.runtime.LocomotionSet;
import dev.shaurmalib.anim.runtime.PlayRequest;
import dev.shaurmalib.anim.runtime.Slot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Тести рушія анімацій без гри. Запуск: {@code java dev.shaurmalib.anim.AnimSmokeTest [шлях_до_maniac_resources]}
 * — з аргументом додатково парсить УСІ реальні анімації маніяка (перевірка, що жоден файл не відкинуто).
 */
public final class AnimSmokeTest {

    static int failed, passed;

    static void check(boolean ok, String name) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (ok) passed++; else failed++;
    }

    static boolean near(float a, float b) { return Math.abs(a - b) < 1e-3f; }
    static boolean near(float a, float b, float eps) { return Math.abs(a - b) < eps; }

    static final List<String> WARN = new ArrayList<>();

    static AnimationClip clip(String json, String rig) {
        return ClipParser.parse(json, "t", "f", rig, WARN::add).get(0);
    }

    /** Простий суб'єкт для правил. */
    static final class Subj implements AnimSubject {
        final UUID id = UUID.randomUUID();
        final Animator a;
        boolean flag;
        Subj(Animator a) { this.a = a; }
        public UUID uuid() { return id; }
        public Object entity() { return this; }
        public AnimInputs inputs() { return a.inputs(); }
        public Animator animator() { return a; }
    }

    static void ticks(Animator a, AnimSubject s, List<AnimBinding> b, int n, List<String> events) {
        for (int i = 0; i < n; i++) a.tick(s, b, (sub, c, e) -> events.add(e.value()));
    }

    public static void main(String[] args) throws Exception {
        json();
        molang();
        channels();
        parserFormats();
        masks();
        bendRig();
        bendMath();
        layering();
        crossfade();
        onceAndEvents();
        bindings();
        headLook();
        locomotion();
        mesh();
        camera();
        network();
        firstPersonToggles();
        emotes();
        Path coreRes = Path.of("src/main/resources");
        if (Files.isDirectory(coreRes)) libraryDefaults(coreRes);
        if (args.length > 0) realFiles(Path.of(args[0]));
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void json() {
        var m = Json.parseObject("{ // коментар\n \"a\": [1, 2.5, -3e1,], \"b\": {\"c\": \"x\\ny\"}, /* блок */ \"d\": true, }");
        check(Json.list(m.get("a")).size() == 3 && Json.num(Json.list(m.get("a")).get(2), 0) == -30, "json: числа, кінцеві коми, коментарі");
        check("x\ny".equals(Json.obj(m.get("b")).get("c")) && Boolean.TRUE.equals(m.get("d")), "json: escape та boolean");
        boolean threw = false;
        try { Json.parse("{\"a\": }"); } catch (IllegalArgumentException e) { threw = e.getMessage().contains("рядок"); }
        check(threw, "json: помилка з номером рядка");
    }

    static void molang() {
        MolangContext ctx = new MolangContext().set("query.anim_time", 1.0);
        check(Molang.compile("math.sin(q.anim_time * 90) * 10").eval(ctx) > 9.99, "molang: sin у градусах + q.alias");
        check(Molang.compile("query.anim_time > 0.5 ? 3 : 4").eval(ctx) == 3, "molang: тернарний і порівняння");
        check(Molang.compile("-(2 + 3) * 2 / 4").eval(ctx) == -2.5, "molang: дужки, унарний мінус");
        check(Molang.compile("math.clamp(5, 0, 2) + math.lerp(0, 10, 0.5)").eval(ctx) == 7, "molang: clamp/lerp");
        check(Molang.compile("v.unknown + 1").eval(ctx) == 1, "molang: невідома змінна = 0");
        check(Molang.compile("t.a = 1; return 5").eval(ctx) == 5, "molang: кілька тверджень / return");
        check(Molang.compile("12.5") instanceof Molang.Const, "molang: число — константа без інтерпретатора");
        check(near((float) Easing.parse("easeInOutSine").apply(0.5), 0.5f) && Easing.parse("easeOutBack").apply(0.7) > 1,
            "easing: парсинг імен Blockbench + перевищення back");
    }

    static void channels() {
        AnimationClip c = clip("{\"animations\":{\"a\":{\"animation_length\":2,\"bones\":{\"head\":{"
            + "\"rotation\":{\"0.0\":[0,0,0],\"1.0\":[10,20,30],\"2.0\":{\"pre\":[10,0,0],\"post\":[50,0,0]}},"
            + "\"position\":{\"0\":{\"vector\":[0,0,0],\"lerp_mode\":\"step\"},\"1\":[4,4,4]},"
            + "\"scale\":2}}}}}", "player");
        float[] o = new float[3];
        MolangContext ctx = new MolangContext();
        c.track("head").rotation().sample(0.5f, ctx, o);
        check(near(o[0], 5) && near(o[1], 10) && near(o[2], 15), "канал: лінійна інтерполяція");
        c.track("head").rotation().sample(1.5f, ctx, o);
        check(near(o[0], 10) && near(o[1], 10), "канал: сегмент іде до pre наступного кадру");
        c.track("head").rotation().sample(5f, ctx, o);
        check(near(o[0], 50), "канал: після кінця — post останнього");
        c.track("head").position().sample(0.9f, ctx, o);
        check(near(o[0], 0), "канал: step тримає значення");
        c.track("head").scale().sample(0.3f, ctx, o);
        check(near(o[0], 2) && near(o[2], 2), "канал: скаляр = рівномірний масштаб");

        AnimationClip cr = clip("{\"animations\":{\"a\":{\"bones\":{\"head\":{\"rotation\":{"
            + "\"0\":{\"post\":[0,0,0],\"lerp_mode\":\"catmullrom\"},\"1\":{\"post\":[10,0,0],\"lerp_mode\":\"catmullrom\"},"
            + "\"2\":{\"post\":[0,0,0],\"lerp_mode\":\"catmullrom\"}}}}}}}", "player");
        cr.track("head").rotation().sample(1f, ctx, o);
        float atKey = o[0];
        cr.track("head").rotation().sample(0.9f, ctx, o);
        check(near(atKey, 10) && o[0] > 9 && o[0] < 10.5f, "канал: catmull-rom проходить через ключі плавно");

        AnimationClip mo = clip("{\"animations\":{\"a\":{\"bones\":{\"head\":{\"rotation\":[\"query.anim_time * 10\",0,0]}}}}}", "player");
        ctx.set("query.anim_time", 3);
        mo.track("head").rotation().sample(0, ctx, o);
        check(near(o[0], 30), "канал: Molang у значенні");
    }

    static void parserFormats() {
        AnimationClip pal = clip("{\"animations\":{\"animation.flash\":{\"loop\":\"hold_on_last_frame\",\"bones\":{"
            + "\"rightArm\":{\"rotation\":{\"0.0\":{\"vector\":[-70,0,0]}}},\"RightItem\":{\"position\":[0,3,1]}}}}}", "player");
        check(pal.id().equals("t:flash") && pal.loop() == AnimationClip.LoopMode.HOLD, "парсер: PAL — префікс animation., hold_on_last_frame");
        check(pal.animates("right_arm") && pal.animates("right_item"), "парсер: camelCase кісток → right_arm/right_item");

        AnimationClip own = ClipParser.parse("{\"format\":\"shaurma_anim\",\"rig\":\"player\",\"animations\":{\"medkit_use\":{"
            + "\"length\":2,\"loop\":\"once\",\"blend_in\":0.1,\"blend_out\":0.3,\"mask\":\"upper_body\",\"slot\":\"action\",\"look\":0.4,"
            + "\"first_person\":\"model\",\"bones\":{\"right_arm\":{\"rotation\":{\"0\":[-80,0,0]}},\"lid\":{\"rotation\":{\"0\":[0,0,0],\"1\":[-110,0,0]}}},"
            + "\"events\":[{\"time\":0.5,\"type\":\"sound\",\"value\":\"t:open\",\"volume\":0.8}],"
            + "\"props\":{\"medkit\":{\"parent\":\"rightArm\",\"model\":\"t:props/medkit\",\"texture\":\"t:textures/x.png\",\"hide_held\":\"right\",\"visible\":{\"0\":true,\"1.8\":false}}}}}}",
            "t", "f", "player", WARN::add).get(0);
        check(own.length() == 2 && own.loop() == AnimationClip.LoopMode.ONCE && "upper_body".equals(own.defaults().mask()),
            "парсер: власний формат — довжина, цикл, маска");
        check(own.defaults().firstPerson() == AnimationClip.FirstPerson.MODEL && near(own.defaults().lookWeight(), 0.4f),
            "парсер: перша особа і погляд");
        check(own.props().size() == 1 && own.props().get(0).parentBone().equals("right_arm")
            && own.props().get(0).visibleAt(1f) && !own.props().get(0).visibleAt(1.9f) && own.props().get(0).hidesRight(),
            "парсер: пропс — батько, видимість, ховання предмета");
        check(own.events().size() == 1 && near((float) own.events().get(0).param("volume", 1), 0.8f), "парсер: події з параметрами");

        AnimationClip bed = ClipParser.parse("{\"format_version\":\"1.8.0\",\"animations\":{\"animation.m.walk\":{\"loop\":true,"
            + "\"animation_length\":1,\"bones\":{\"left_leg\":{\"rotation\":{\"0\":[30,0,0],\"0.5\":[-30,0,0],\"1\":[30,0,0]}}},"
            + "\"sound_effects\":{\"0.5\":{\"effect\":\"step\"}},\"timeline\":{\"0.25\":[\"v.x = 1;\"]}}}}",
            "maniacmod", "test_maniac", "geo:maniacmod:test_maniac", WARN::add).get(0);
        check(bed.id().equals("maniacmod:test_maniac/m.walk") && bed.loop() == AnimationClip.LoopMode.LOOP && bed.events().size() == 2,
            "парсер: GeckoLib/Bedrock — id з файлом, sound_effects і timeline");

        AnimResourceLoader rl = new AnimResourceLoader(WARN::add);
        rl.accept("t", "player_animations/x.json", "{\"animations\":{\"animation.x\":{\"bones\":{\"head\":{\"rotation\":[1,0,0]}}}}}");
        rl.accept("t", "shaurma_anim/player/x.json", "{\"format\":\"shaurma_anim\",\"animations\":{\"x\":{\"bones\":{\"head\":{\"rotation\":[2,0,0]}}}}}");
        rl.accept("t", "player_animations/x.json", "{\"animations\":{\"animation.x\":{\"bones\":{\"head\":{\"rotation\":[3,0,0]}}}}}");
        float[] o = new float[3];
        rl.clips().get("t:x").track("head").rotation().sample(0, new MolangContext(), o);
        check(near(o[0], 2), "завантажувач: shaurma_anim/ пріоритетніший за старі теки");
        int before = WARN.size();
        rl.accept("t", "shaurma_anim/bad.json", "{ broken");
        check(WARN.size() == before + 1, "завантажувач: битий файл → попередження, не виняток");
        check(!AnimResourceLoader.accepts("models/item/x.json") && AnimResourceLoader.accepts("animations/item/a.animation.json"),
            "завантажувач: фільтр шляхів");
    }

    static void bendRig() {
        Rig r = Rig.PLAYER;
        int rf = r.indexOf("right_forearm"), lf = r.indexOf("left_forearm"), rs = r.indexOf("right_shin"), ls = r.indexOf("left_shin");
        check(rf >= 0 && lf >= 0 && rs >= 0 && ls >= 0, "згин: риг має передпліччя і гомілки");
        check(r.isDescendant(r.indexOf("right_item"), rf) && r.isDescendant(r.indexOf("left_item"), lf),
            "згин: предмет у руці — нащадок передпліччя");
        check(r.indexOf("Right_Arm_Lower") == rf && r.indexOf("leftLowerLeg") == ls && r.indexOf("right_knee") == rs
            && r.indexOf("forearmLeft") == lf, "згин: синоніми (DragonCore, camelCase, лікоть/коліно)");
        check(r.mask("arms").weight(rf) == 1 && r.mask("right_arm").weight(rf) == 1 && r.mask("right_arm").weight(lf) == 0
            && r.mask("left_arm").weight(lf) == 1, "згин: маски рук включають передпліччя");
        check(r.mask("legs").weight(rs) == 1 && r.mask("legs").weight(ls) == 1 && r.mask("upper_body").weight(rs) == 0
            && r.mask("full-legs").weight(ls) == 0 && r.mask("upper_body").weight(rf) == 1, "згин: гомілки — у legs, не в upper_body");
        check(r.mask("right_arm!").weight(rf) == 0, "згин: right_arm! — без передпліччя");
        check(r.mask("spine").weight(rf) == 0 && r.mask("spine").weight(rs) == 0, "згин: spine без кінцівок");
    }

    static void bendMath() {
        dev.shaurmalib.anim.bend.BendMath b = new dev.shaurmalib.anim.bend.BendMath();
        // права рука у просторі ModelPart: кубоїд (-3,-2,-2)..(1,10,2), шарнір посередині
        float[] h = new float[3];
        dev.shaurmalib.anim.bend.BendMath.cubeHinge(-3, -2, -2, 1, 10, 2, h);
        check(near(h[0], -1) && near(h[1], 4) && near(h[2], 0), "згин: шарнір = центр кубоїда");

        float[] o = new float[3];
        check(!b.set(0, 0, 0, h[0], h[1], h[2]), "згин: нульовий кут → неактивний");
        b.transform(-3, 10, 2, o);
        check(near(o[0], -3) && near(o[1], 10) && near(o[2], 2), "згин: нуль → вершина на місці");
        check(b.region(-2) == -1 && b.region(4) == 0 && b.region(10) == 1, "згин: кільця upper/mid/lower");

        float[] q = new float[4];
        dev.shaurmalib.anim.bend.BendMath.eulerZYX((float) Math.toRadians(90), 0, 0, q);
        dev.shaurmalib.anim.bend.BendMath.rotate(q, 0, 1, 0, o);
        check(near(o[0], 0) && near(o[1], 0) && near(o[2], 1), "згин: Rx(90)·Y = Z (як ModelPart)");

        // miter: точка шарніра лежить і на верхньому, і на нижньому ребрі — для будь-якого чистого згину
        float[][] corners = { { -3, 4, -2 }, { 1, 4, -2 }, { 1, 4, 2 }, { -3, 4, 2 } };
        float[][] rots = { { 1, 0, 0 }, { 0, 0, 1 }, { 1, 0, 1 }, { -1, 0, 0.5f } };
        boolean miterOk = true, lowerRigid = true;
        float[] qy = new float[3], m = new float[3], low = new float[3];
        for (float[] dir : rots) {
            for (int deg = -150; deg <= 150; deg += 15) {
                if (deg == 0) continue;
                float len = (float) Math.sqrt(dir[0] * dir[0] + dir[2] * dir[2]);
                // Ейлер X+Z разом дає і трохи twist — miter для таких перевіряємо лише без twist (нижче),
                // жорсткість нижнього сегмента — для всіх
                b.set(deg * dir[0] / len, 0, deg * dir[2] / len, h[0], h[1], h[2]);
                float[] full = b.lowerRotation();
                dev.shaurmalib.anim.bend.BendMath.rotate(full, 0, 1, 0, qy);
                for (float[] c : corners) {
                    b.mid(c[0], c[1], c[2], m);
                    b.lower(c[0], c[1], c[2], low); // = точка нижнього ребра при s = 0
                    // на верхньому ребрі: x, z не змінились (ребро вертикальне), якщо twist = 0
                    float twistY = full[1];
                    boolean noTwist = Math.abs(twistY) < 1e-4f;
                    if (noTwist && (!near(m[0], c[0], 1e-3f) || !near(m[2], c[2], 1e-3f))) miterOk = false;
                    // на нижньому ребрі: (m − low) ∥ q·Y
                    float dx = m[0] - low[0], dy = m[1] - low[1], dz = m[2] - low[2];
                    float cx = dy * qy[2] - dz * qy[1], cy = dz * qy[0] - dx * qy[2], cz = dx * qy[1] - dy * qy[0];
                    if (noTwist && Math.sqrt(cx * cx + cy * cy + cz * cz) > 2e-3f) miterOk = false;
                    // нижній сегмент жорсткий: відстань до шарніра не змінилась
                    b.lower(c[0], 10, c[2], low);
                    float d0 = dist(c[0], 10, c[2], h), d1 = dist(low[0], low[1], low[2], h);
                    if (!near(d0, d1, 1e-3f)) lowerRigid = false;
                }
            }
        }
        check(miterOk, "згин: miter — шарнір на перетині верхнього і нижнього ребер (без щілин)");
        check(lowerRigid, "згин: нижній сегмент обертається жорстко");

        // кут 90° по X: товщина ліктя по осі згину не змінилась, зовнішній кут витягнувся на 1/cos(45°)
        b.set(90, 0, 0, h[0], h[1], h[2]);
        b.mid(-1, 4, 2, o);
        float back = o[2] - h[2], drop = o[1] - h[1];
        check(near(back, 2) && near(Math.abs(drop), 2), "згин: 90° → точка шарніра на бісектрисі 45°");
        b.mid(-3, 4, 0, o);
        check(near(o[0], -3) && near(o[1], 4) && near(o[2], 0), "згин: вершини на осі згину нерухомі");

        // обмеження кута
        b.set(179, 0, 0, h[0], h[1], h[2]);
        check(near((float) Math.toDegrees(b.swingRadians()), dev.shaurmalib.anim.bend.BendMath.MAX_SWING_DEG, 0.05f),
            "згин: кут обмежено MAX_SWING_DEG");

        // чистий twist: шарнір повертається наполовину, радіус зберігається, нижній — повністю
        check(b.set(0, 90, 0, h[0], h[1], h[2]) && near(b.swingRadians(), 0), "згин: twist без swing — активний, swing 0");
        b.mid(1, 4, 0, o);
        float r = (float) Math.hypot(o[0] - h[0], o[2] - h[2]);
        check(near(r, 2) && near(o[1], 4), "згин: twist — шарнір на тому ж радіусі й висоті");
        check(near((float) Math.toDegrees(Math.atan2(-(o[2] - h[2]), o[0] - h[0])), 45, 0.05f), "згин: twist — шарнір на половині кута");
        b.lower(1, 10, 0, o);
        check(near(o[0], h[0]) && near(o[2], h[2] - 2) && near(o[1], 10), "згин: twist — нижнє кільце на повному куті");

        // півот на зовнішньому ребрі: рука 4×12×4, лікоть уперед (-90° по X — нижній сегмент іде в −Z)
        b.set(-90, 0, 0, h[0], h[1], h[2], 2, 2, 6);
        float[] pv = b.pivot();
        check(near(pv[0], -1) && near(pv[1], 4) && near(pv[2], 2), "згин: півот на опуклому боці (зад ліктя, +Z)");
        float[] top = { -1, -2, 2 }, mo = new float[3], lo = new float[3];
        b.mid(-1, 4, 2, mo);
        b.lower(-1, 10, 2, lo);
        float outer = dist(top[0], top[1], top[2], mo) + dist(lo[0], lo[1], lo[2], mo);
        check(near(outer, 12, 1e-3f), "згин: зовнішній бік ліктя не розтягується (12 px)");
        b.mid(-1, 4, -2, mo);
        b.lower(-1, 10, -2, lo);
        check(near(mo[1], 0) && near(lo[0], -1) && near(lo[1], 0) && near(lo[2], -4),
            "згин: внутрішній бік складається в згин (miter на 90°)");
        // дуже глибокий згин: внутрішнє кільце не виходить за торець сегмента
        b.set(-150, 0, 0, h[0], h[1], h[2], 2, 2, 6);
        b.mid(-1, 4, -2, mo);
        check(Math.abs(mo[1] - h[1]) <= 6 - dev.shaurmalib.anim.bend.BendMath.MID_MARGIN + 1e-4f,
            "згин: 150° — внутрішнє кільце обмежене довжиною сегмента");
        b.mid(-1, 4, 2, mo);
        check(near(mo[0], -1) && near(mo[1], 4) && near(mo[2], 2), "згин: 150° — зовнішнє ребро на півоті, без шпиля");
        // жорсткість нижнього сегмента з півотом і twist: попарні відстані зберігаються
        b.set(-70, 30, 20, h[0], h[1], h[2], 2, 2, 6);
        float[] p1 = new float[3], p2 = new float[3];
        b.lower(-3, 10, -2, p1);
        b.lower(1, 7, 2, p2);
        check(near(dist(p1[0], p1[1], p1[2], p2), dist(-3, 10, -2, new float[] { 1, 7, 2 }), 1e-3f),
            "згин: півот + twist — нижній сегмент жорсткий");

        // нормаль нижнього сегмента
        b.set(90, 0, 0, h[0], h[1], h[2]);
        b.lowerNormal(0, 1, 0, o);
        check(near(o[0], 0) && near(o[1], 0) && near(o[2], 1), "згин: нормаль низу обертається з сегментом");
    }

    static float dist(float x, float y, float z, float[] p) {
        return (float) Math.sqrt((x - p[0]) * (x - p[0]) + (y - p[1]) * (y - p[1]) + (z - p[2]) * (z - p[2]));
    }

    static void masks() {
        Rig r = Rig.PLAYER;
        BoneMask upper = r.mask("upper_body");
        check(upper.weight(r.indexOf("right_arm")) == 1 && upper.weight(r.indexOf("head")) == 1
            && upper.weight(r.indexOf("right_leg")) == 0 && upper.weight(r.indexOf("body")) == 0, "маска: upper_body без ніг і кореня");
        check(r.mask("right_arm").weight(r.indexOf("right_item")) == 1 && r.mask("right_arm").weight(r.indexOf("left_arm")) == 0,
            "маска: кістка тягне нащадків (рука + предмет)");
        BoneMask m = r.mask("full-legs");
        check(m.weight(r.indexOf("left_leg")) == 0 && m.weight(r.indexOf("torso")) == 1 && m.weight(r.indexOf("body")) == 1, "маска: віднімання");
        BoneMask w = r.mask("right_arm+torso*0.5");
        check(near(w.weight(r.indexOf("torso")), 0.5f) && w.weight(r.indexOf("right_arm")) == 1, "маска: вага терма");
        check(r.mask("body!").weight(r.indexOf("head")) == 0 && r.mask("body!").weight(r.indexOf("body")) == 1, "маска: ! — лише кістка");
        check(r.mask("-legs").weight(r.indexOf("right_leg")) == 0 && r.mask("-legs").weight(r.indexOf("head")) == 1, "маска: -legs = full-legs");
        check(upper.weight(-1) == 1, "маска: кістки пропсів завжди 1");

        Rig geo = Rig.fromHierarchy("geo:t:maniac", List.of("root", "body", "head", "left_arm", "right_arm", "left_leg", "right_leg"),
            java.util.Arrays.asList(null, "root", "body", "body", "body", "root", "root"), null);
        BoneMask gu = geo.mask("upper_body");
        check(gu.weight(geo.indexOf("body")) == 1 && gu.weight(geo.indexOf("right_arm")) == 1 && gu.weight(geo.indexOf("left_leg")) == 0
            && gu.weight(geo.indexOf("root")) == 0, "маска geo: upper_body виведено з ієрархії маньяка");
        check(geo.indexOf("HEAD") == geo.indexOf("head"), "риг geo: імена без урахування регістру");
    }

    static final String LIB_JSON = "{\"format\":\"shaurma_anim\",\"rig\":\"player\",\"animations\":{"
        + "\"walk\":{\"loop\":true,\"length\":1,\"bones\":{\"right_leg\":{\"rotation\":{\"0\":[40,0,0],\"0.5\":[-40,0,0],\"1\":[40,0,0]}},"
        + "\"right_arm\":{\"rotation\":{\"0\":[-20,0,0],\"0.5\":[20,0,0],\"1\":[-20,0,0]}}}},"
        + "\"idle\":{\"loop\":true,\"length\":2,\"bones\":{\"right_arm\":{\"rotation\":[0,0,5]}}},"
        + "\"medkit\":{\"length\":2,\"loop\":\"once\",\"mask\":\"upper_body\",\"blend_in\":0,\"blend_out\":0,"
        + "\"bones\":{\"right_arm\":{\"rotation\":[-80,0,0]},\"right_leg\":{\"rotation\":[99,0,0]}},"
        + "\"events\":[{\"time\":0,\"type\":\"sound\",\"value\":\"start\"},{\"time\":1,\"type\":\"sound\",\"value\":\"open\"}]},"
        + "\"loop_ev\":{\"length\":0.5,\"loop\":true,\"bones\":{\"head\":{\"rotation\":[1,0,0]}},\"events\":[{\"time\":0.25,\"value\":\"tick\"}]},"
        + "\"a\":{\"loop\":true,\"bones\":{\"right_arm\":{\"rotation\":[-90,0,0]}}},"
        + "\"b\":{\"loop\":true,\"bones\":{\"right_arm\":{\"rotation\":[0,0,0]}}},"
        + "\"pose\":{\"loop\":\"hold\",\"look\":0.5,\"bones\":{\"head\":{\"rotation\":[30,0,0]},\"body\":{\"rotation\":[-90,0,0]}}},"
        + "\"repair\":{\"loop\":true,\"bones\":{\"right_arm\":{\"rotation\":[-60,0,0]}}},"
        + "\"refuel\":{\"loop\":true,\"bones\":{\"right_arm\":{\"rotation\":[-30,0,0]}}}"
        + "}}";

    static ClipLibrary lib() {
        ClipLibrary lib = new ClipLibrary();
        for (AnimationClip c : ClipParser.parse(LIB_JSON, "t", "lib", "player", WARN::add)) lib.register(c);
        return lib;
    }

    static void layering() {
        ClipLibrary lib = lib();
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.locomotion(LocomotionSet.create().set(LocomotionSet.State.WALK, "t:walk").set(LocomotionSet.State.IDLE, "t:idle"));
        a.inputs().limbSwingAmount = 1f;
        List<String> ev = new ArrayList<>();
        ticks(a, null, null, 20, ev);
        check(a.locomotionWeight(LocomotionSet.State.WALK) > 0.95f, "локомоція: при ходьбі вага walk → 1");
        a.play(PlayRequest.of("t:medkit"));
        ticks(a, null, null, 5, ev);
        Pose out = new Pose(Rig.PLAYER);
        a.inputs().limbSwing = Animator.VANILLA_STRIDE * 0.5f; // фаза пів кроку
        a.evaluate(1f, null, false, out);
        check(near(out.bone("right_arm").rx, -80, 0.5f), "шари: аптечка (upper_body) веде руку");
        check(near(out.bone("right_leg").rx, -40, 1f), "шари: ноги лишаються від ходьби (маска не пускає 99°)");

        Pose p1 = new Pose(Rig.PLAYER), p2 = new Pose(Rig.PLAYER);
        a.evaluate(0.37f, null, false, p1);
        a.evaluate(0.37f, null, false, p2);
        check(p1.bone("right_arm").rx == p2.bone("right_arm").rx && p1.bone("right_leg").rx == p2.bone("right_leg").rx,
            "детермінізм: той самий кадр — та сама поза");

        Pose base = new Pose(Rig.PLAYER);
        base.bone("left_arm").rx = 12;
        Animator b = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        b.play(PlayRequest.of("t:a").fade(0, 0));
        b.tick(null, null, null);
        b.evaluate(1f, base, true, out);
        check(near(out.bone("left_arm").rx, 12) && !out.touched("left_arm") && out.touched("right_arm"),
            "база: кістки поза кліпом лишаються ванільними і не позначені");
    }

    static void crossfade() {
        ClipLibrary lib = lib();
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.play(PlayRequest.of("t:a").fade(0, 0).slot(Slot.HOLD));
        for (int i = 0; i < 3; i++) a.tick(null, null, null);
        Pose out = new Pose(Rig.PLAYER);
        a.evaluate(1f, null, false, out);
        float before = out.bone("right_arm").rx;
        a.play(PlayRequest.of("t:b").fade(0.5f, 0.2f).slot(Slot.HOLD));
        float max = 0, prev = before;
        for (int i = 0; i < 12; i++) {
            a.tick(null, null, null);
            for (int f = 0; f < 4; f++) {
                a.evaluate(f / 4f, null, false, out);
                max = Math.max(max, Math.abs(out.bone("right_arm").rx - prev));
                prev = out.bone("right_arm").rx;
            }
        }
        check(near(before, -90) && near(prev, 0, 0.01f), "кросфейд: від -90 до 0");
        check(max < 15f, "кросфейд: жодного стрибка між кадрами (макс крок " + max + "°)");
        check(a.current(Slot.HOLD).clip().id().equals("t:b"), "кросфейд: поточний — новий кліп");
        check(a.play(PlayRequest.of("t:b").slot(Slot.HOLD)) && a.current(Slot.HOLD).clip().id().equals("t:b"),
            "повторний play того самого кліпа не перезапускає");
        check(!a.play(PlayRequest.of("t:nope")), "play невідомого кліпа = false");
    }

    static void onceAndEvents() {
        ClipLibrary lib = lib();
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        List<String> ev = new ArrayList<>();
        a.play(PlayRequest.of("t:medkit"));
        ticks(a, null, null, 30, ev);
        check(ev.equals(List.of("start", "open")), "події: кожна рівно раз, з подією на 0.0 — " + ev);
        ticks(a, null, null, 15, ev);
        check(a.current(Slot.ACTION) == null, "ONCE: після кінця шар порожній");
        check(ev.size() == 2, "події: після кінця нових немає");

        Animator l = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        List<String> lev = new ArrayList<>();
        l.play(PlayRequest.of("t:loop_ev").fade(0, 0));
        ticks(l, null, null, 40, lev); // 2 с, цикл 0.5 с → 4 рази
        check(lev.size() == 4, "події циклу: раз на кожен цикл (" + lev.size() + ")");

        Animator s = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        s.play(PlayRequest.of("t:a").fade(0, 0.25f));
        s.tick(null, null, null);
        s.stop(Slot.ACTION);
        Pose out = new Pose(Rig.PLAYER);
        s.tick(null, null, null);
        s.evaluate(1f, null, false, out);
        float mid = out.bone("right_arm").rx;
        for (int i = 0; i < 10; i++) s.tick(null, null, null);
        s.evaluate(1f, null, false, out);
        check(mid < -5 && mid > -89 && !out.touched("right_arm") && s.current(Slot.ACTION) == null,
            "stop: плавне згасання, потім кістка вільна");
    }

    static void bindings() {
        ClipLibrary lib = lib();
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        Subj s = new Subj(a);
        List<AnimBinding> b = new ArrayList<>();
        b.add(AnimBinding.when("pose", x -> ((Subj) x.entity()).flag).play("t:pose").slot(Slot.POSE));
        b.add(AnimBinding.when("work", x -> true).play(x -> ((Subj) x.entity()).flag ? "t:refuel" : "t:repair").slot(Slot.ACTION));
        b.add(AnimBinding.when("work_lo", x -> true).play("t:a").slot(Slot.ACTION).priority(-1));
        b.add(AnimBinding.holding("t:flashlight", "t:idle").mask("right_arm"));
        List<String> ev = new ArrayList<>();
        ticks(a, s, b, 2, ev);
        check(a.current(Slot.POSE) == null && a.current(Slot.ACTION).clip().id().equals("t:repair"), "правило: вища пріоритетність перемагає");
        s.flag = true;
        ticks(a, s, b, 2, ev);
        check(a.current(Slot.POSE) != null && a.current(Slot.ACTION).clip().id().equals("t:refuel"),
            "правило: умова true вмикає позу; селектор міняє кліп у тому ж шарі");
        s.flag = false;
        ticks(a, s, b, 20, ev);
        check(a.current(Slot.POSE) == null, "правило: умова false — поза згасла");
        a.play(PlayRequest.of("t:medkit"));
        ticks(a, s, b, 3, ev);
        check(a.current(Slot.ACTION).clip().id().equals("t:medkit"), "правило не перебиває ручний play");
        ticks(a, s, b, 50, ev);
        check(a.current(Slot.ACTION) != null && a.current(Slot.ACTION).clip().id().equals("t:repair"),
            "правило повертається після ручної дії");
        a.inputs().mainHandItem = "t:flashlight";
        ticks(a, s, b, 2, ev);
        check(a.current(Slot.HOLD) != null && a.current(Slot.HOLD).clip().id().equals("t:idle"), "holding(): поза тримання предмета");

        List<AnimBinding> bad = List.of(AnimBinding.when("boom", x -> { throw new IllegalStateException(); }).play("t:a"));
        boolean ok = true;
        try { ticks(a, s, bad, 1, ev); } catch (RuntimeException e) { ok = false; }
        check(ok, "правило з винятком не валить тік");
    }

    static void headLook() {
        ClipLibrary lib = lib();
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.play(PlayRequest.of("t:pose").fade(0, 0));
        a.tick(null, null, null);
        a.inputs().netHeadYaw = 40;
        a.inputs().headPitch = 10;
        Pose base = new Pose(Rig.PLAYER);
        base.bone("head").ry = 40;  // ваніль уже повернула голову
        base.bone("head").rx = 10;
        Pose out = new Pose(Rig.PLAYER);
        a.evaluate(1f, base, true, out);
        check(near(out.bone("head").ry, 20) && near(out.bone("head").rx, 35),
            "погляд: поза з look=0.5 лишає пів повороту голови (" + out.bone("head") + ")");
        Animator g = new Animator(UUID.randomUUID(), Rig.fromHierarchy("geo:x", List.of("root", "head"),
            java.util.Arrays.asList(null, "root"), null), lib).lookLimits(30, 30);
        g.inputs().netHeadYaw = 80;
        g.tick(null, null, null);
        Pose go = new Pose(g.rig());
        g.evaluate(1f, null, false, go);
        check(near(go.bone("head").ry, 30) && go.touched("head"), "погляд geo: процедурний, з межею шиї");
    }

    static void locomotion() {
        ClipLibrary lib = lib();
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.locomotion(LocomotionSet.create().set(LocomotionSet.State.WALK, "t:walk")); // без idle
        a.inputs().limbSwingAmount = 0f;
        for (int i = 0; i < 20; i++) a.tick(null, null, null);
        Pose base = new Pose(Rig.PLAYER);
        base.bone("right_leg").rx = 7;
        Pose out = new Pose(Rig.PLAYER);
        a.evaluate(1f, base, true, out);
        check(near(out.bone("right_leg").rx, 7), "локомоція: нема idle — база (ваніль) проходить");
        a.inputs().limbSwingAmount = 1f;
        for (int i = 0; i < 20; i++) a.tick(null, null, null);
        a.inputs().limbSwing = 0;
        a.evaluate(1f, base, true, out);
        float l0 = out.bone("right_leg").rx;
        a.inputs().limbSwing = Animator.VANILLA_STRIDE * 0.5f;
        a.evaluate(1f, base, true, out);
        float l1 = out.bone("right_leg").rx;
        check(near(l0, 40, 1) && near(l1, -40, 1), "локомоція: фаза ходьби — від відстані кроку, не від часу");
        a.inputs().sprinting = true;
        for (int i = 0; i < 20; i++) a.tick(null, null, null);
        check(a.locomotionWeight(LocomotionSet.State.WALK) > 0.95f, "локомоція: нема run — запасний walk");
        a.inputs().onGround = false;
        a.inputs().verticalSpeed = -0.3f;
        for (int i = 0; i < 20; i++) a.tick(null, null, null);
        check(a.locomotionWeight(LocomotionSet.State.WALK) < 0.05f, "локомоція: у повітрі без jump/fall — вага віддана базі");

        // руки поступаються ваніллю, поки використовується предмет (лук/щит не ламаються)
        Animator y = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        y.locomotion(LocomotionSet.create().set(LocomotionSet.State.WALK, "t:walk"));
        y.inputs().limbSwingAmount = 1f;
        y.inputs().usingItem = true;
        for (int i = 0; i < 20; i++) y.tick(null, null, null);
        Pose yb = new Pose(Rig.PLAYER);
        yb.bone("right_arm").rx = -90; // ваніль цілиться з лука
        y.inputs().limbSwing = 0;
        y.evaluate(1f, yb, true, out);
        check(near(out.bone("right_arm").rx, -90, 1) && near(out.bone("right_leg").rx, 40, 1),
            "локомоція: при використанні предмета руки — ванільні, ноги — ходьба");
        swim(lib);
    }

    static void swim(ClipLibrary lib) {
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.locomotion(LocomotionSet.create().set(LocomotionSet.State.WALK, "t:walk").set(LocomotionSet.State.IDLE, "t:idle"));
        a.inputs().swimming = true;
        a.inputs().limbSwingAmount = 1f;
        for (int i = 0; i < 20; i++) a.tick(null, null, null);
        check(a.locomotionWeight(LocomotionSet.State.WALK) < 0.05f && a.locomotionWeight(LocomotionSet.State.IDLE) < 0.05f,
            "локомоція: плавання без кліпа — ванільна поза, а не цикл ходьби");
    }

    /** Вбудована локомоція бібліотеки (core/src/main/resources) парситься і дає цикл. */
    static void libraryDefaults(Path coreResources) throws Exception {
        Path p = coreResources.resolve("assets/shaurma_lib/shaurma_anim/player/locomotion.json");
        AnimResourceLoader rl = new AnimResourceLoader(WARN::add);
        int before = WARN.size();
        rl.accept("shaurma_lib", "shaurma_anim/player/locomotion.json", Files.readString(p, StandardCharsets.UTF_8));
        check(WARN.size() == before && rl.clips().size() == 8, "бібліотека: 8 кліпів локомоції гравця без попереджень");
        LocomotionSet def = ShaurmaAnim.defaultPlayerLocomotion();
        boolean all = true;
        for (LocomotionSet.State s : List.of(LocomotionSet.State.IDLE, LocomotionSet.State.WALK, LocomotionSet.State.RUN,
                LocomotionSet.State.SNEAK, LocomotionSet.State.SNEAK_WALK, LocomotionSet.State.JUMP, LocomotionSet.State.FALL)) {
            if (!rl.clips().containsKey(def.clip(s))) { all = false; System.out.println("  немає " + def.clip(s)); }
        }
        check(all && rl.clips().containsKey(def.attackClip()), "бібліотека: усі id з defaultPlayerLocomotion існують");
        ClipLibrary lib = new ClipLibrary();
        rl.clips().values().forEach(lib::register);
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.locomotion(def);
        a.inputs().limbSwingAmount = 1f;
        for (int i = 0; i < 20; i++) a.tick(null, null, null);
        Pose out = new Pose(Rig.PLAYER);
        a.inputs().limbSwing = 0;
        a.evaluate(1f, null, true, out);
        float r0 = out.bone("right_leg").rx;
        a.inputs().limbSwing = Animator.VANILLA_STRIDE / 2;
        a.evaluate(1f, null, true, out);
        check(r0 > 25 && out.bone("right_leg").rx < -25, "бібліотека: ходьба — нога вперед/назад за фазою (" + r0 + ")");
    }

    static void mesh() {
        String geo = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.medkit\","
            + "\"texture_width\":32,\"texture_height\":32},\"bones\":[{\"name\":\"medkit\",\"parent\":\"right_arm\",\"pivot\":[-5,22,0],"
            + "\"cubes\":[{\"origin\":[-6,10,-3],\"size\":[2,2,2],\"uv\":[0,0]}]},"
            + "{\"name\":\"lid\",\"parent\":\"medkit\",\"pivot\":[-5,12,0],\"cubes\":[{\"origin\":[-6,12,-3],\"size\":[2,1,2],"
            + "\"uv\":{\"north\":{\"uv\":[0,0],\"uv_size\":[2,1]}}}]}]}]}";
        GeoMesh m = GeoMesh.parse(geo);
        check(m.bones.size() == 2 && m.roots.size() == 1 && m.bone("lid").parent == m.bone("medkit"), "mesh: ієрархія; чужий батько = корінь пропса");
        check(m.bone("medkit").cubes.get(0).quads.size() == 6 && m.bone("lid").cubes.get(0).quads.size() == 1, "mesh: box-UV 6 граней, per-face лише задані");
        check(near(m.bone("medkit").pivotX, 5 / 16f), "mesh: півот дзеркалиться по X (як GeckoLib)");
        float[] bounds = { 9, -9 };
        PropBaker.bake(m, new float[] { 5, 22, 0 }, null, (pos, uv, nx, ny, nz) -> {
            for (int v = 0; v < 4; v++) { bounds[0] = Math.min(bounds[0], pos[v * 3 + 1]); bounds[1] = Math.max(bounds[1], pos[v * 3 + 1]); }
        });
        check(near(bounds[0], (10 - 22) / 16f) && near(bounds[1], (13 - 22) / 16f), "PropBaker: вершини відносно півота руки");
        int[] quads = { 0 };
        PropBaker.bake(m, new float[] { 5, 22, 0 }, (bone, type, out) -> {
            if (bone.equals("lid") && type == dev.shaurmalib.anim.clip.BoneTrack.ChannelType.SCALE) { out[0] = out[1] = out[2] = 0; return true; }
            return false;
        }, (pos, uv, nx, ny, nz) -> quads[0]++);
        check(quads[0] == 6, "PropBaker: масштаб 0 ховає кістку (кришка)");
    }

    static void camera() {
        dev.shaurmalib.anim.runtime.CameraSolver.Frame f = new dev.shaurmalib.anim.runtime.CameraSolver.Frame();
        Pose p = new Pose(Rig.PLAYER);
        check(!dev.shaurmalib.anim.runtime.CameraSolver.solve(p, f), "камера: без ваги — вимкнена");
        p.cameraWeight = 1;
        p.cameraRotWeight = 1;
        dev.shaurmalib.anim.runtime.CameraSolver.solve(p, f);
        check(near(f.eyeY, 25.92f) && near(f.eyeZ, 0) && near(f.pitch, 0) && near(f.yaw, 0), "камера: стоячи — око на 1.62, кути 0");

        p.bone("body").rx = -90; // корінь: лягти на спину навколо стегон
        dev.shaurmalib.anim.runtime.CameraSolver.solve(p, f);
        check(near(f.eyeY, 12, 0.01f) && near(f.eyeZ, 13.92f, 0.01f) && near(f.pitch, -90, 0.1f),
            "камера: лежить на спині — око біля землі позаду стегон, дивиться вгору (" + f.eyeY + "," + f.eyeZ + "," + f.pitch + ")");

        p.bone("body").rx = 0;
        p.headNoLook.ry = 30;
        p.headNoLook.rx = 20;
        dev.shaurmalib.anim.runtime.CameraSolver.solve(p, f);
        check(near(f.yaw, 30, 0.1f) && near(f.pitch, 20, 0.1f), "камера: поворот голови в кліпі → yaw/pitch камери");
        p.headNoLook.identity();
        p.bone("body").rz = 25;
        dev.shaurmalib.anim.runtime.CameraSolver.solve(p, f);
        check(Math.abs(f.roll) > 20 && f.eyeX < -1, "камера: нахил тіла вбік → крен і зсув ока");

        // вага з кліпа/запиту: плавно вмикається з fade і процедурний погляд не рахується двічі
        ClipLibrary lib = lib();
        Animator a = new Animator(UUID.randomUUID(), Rig.PLAYER, lib);
        a.play(PlayRequest.of("t:pose").fade(0.5f, 0.2f).camera(1f, 0.6f));
        a.inputs().netHeadYaw = 50;
        a.tick(null, null, null);
        Pose out = new Pose(Rig.PLAYER);
        a.evaluate(1f, null, false, out);
        float early = out.cameraWeight;
        for (int i = 0; i < 20; i++) a.tick(null, null, null);
        a.evaluate(1f, null, false, out);
        check(early > 0 && early < 0.5f && near(out.cameraWeight, 1) && near(out.cameraRotWeight, 0.6f), "камера: вага з fade (" + early + ")");
        check(near(out.headNoLook.ry, 0) && out.bone("head").ry > 10, "камера: голова без погляду окремо від голови з поглядом");
    }

    static void firstPersonToggles() {
        ShaurmaAnim.resetFirstPersonOverrides();
        ShaurmaAnim.applyFirstPersonCompat(false, false, "");
        check(ShaurmaAnim.firstPersonArms() && ShaurmaAnim.headCamera(), "перша особа: за замовчуванням руки й камера увімкнені");
        ShaurmaAnim.applyFirstPersonCompat(true, false, "firstperson");
        check(!ShaurmaAnim.firstPersonArms() && ShaurmaAnim.headCamera(), "перша особа: мод тіла вимикає руки, камеру лишає");
        ShaurmaAnim.applyFirstPersonCompat(true, true, "realcamera");
        check(!ShaurmaAnim.headCamera(), "перша особа: мод, що веде камеру, вимикає камеру рушія");
        ShaurmaAnim.setFirstPersonArms(true);
        ShaurmaAnim.setHeadCamera(true);
        check(ShaurmaAnim.firstPersonArms() && ShaurmaAnim.headCamera(), "перша особа: явний вибір мода перекриває автодетект");
        ShaurmaAnim.setFirstPersonArms(false);
        ShaurmaAnim.applyFirstPersonCompat(false, false, "");
        check(!ShaurmaAnim.firstPersonArms(), "перша особа: явне вимкнення не скасовується автодетектом");
        ShaurmaAnim.resetFirstPersonOverrides();
        check(ShaurmaAnim.firstPersonArms(), "перша особа: reset повертає автоматику");
    }

    static void emotes() throws Exception {
        Emote wave = Emote.builder("t:wave", "t:emote/wave").name("Помах").category("social").build();
        Emote sit = Emote.builder("t:sit", "t:emote/sit").name("Сісти").fullBody().loop(true).build();
        Emote dance = Emote.builder("t:dance", "t:emote/dance").category("fun").fullBody().interruptOnMove(false).build();
        check(wave.canMove() && !wave.interruptOnMove() && Emote.MASK_UPPER.equals(wave.mask()) && wave.loop() == null,
            "емоції: upper_body — можна йти, рух не перериває, повтор з кліпа");
        check(!sit.canMove() && sit.interruptOnMove() && Boolean.TRUE.equals(sit.loop()) && "general".equals(sit.category()),
            "емоції: full — рух перериває за замовчуванням, категорія за замовчуванням");
        check(!dance.canMove() && !dance.interruptOnMove(), "емоції: interruptOnMove можна перекрити");
        check("t:dance".equals(dance.name()), "емоції: без назви — id");

        EmoteRegistry reg = new EmoteRegistry();
        reg.register(wave);
        reg.register(sit);
        reg.register(dance);
        check(reg.available().size() == 3 && reg.available().get(0) == wave && reg.categories().equals(List.of("social", "general", "fun")),
            "реєстр: порядок реєстрації, категорії");
        reg.register(Emote.builder("t:wave", "t:emote/wave2").build());
        check(reg.size() == 3 && reg.get("t:wave").clip().equals("t:emote/wave2") && reg.available().get(0).id().equals("t:wave"),
            "реєстр: повторна реєстрація замінює на місці");
        reg.setFilter(e -> !e.category().equals("fun"));
        check(reg.available("t:dance") == null && reg.get("t:dance") != null && reg.available().size() == 2,
            "реєстр: фільтр режиму ховає, але не видаляє");
        reg.setFilter(e -> { throw new IllegalStateException(); });
        check(reg.available().isEmpty(), "реєстр: кривий фільтр = недоступно, без винятку");
        reg.setFilter(null);

        EmoteWheel w = new EmoteWheel();
        check(!w.configured() && w.get(0) == null, "слоти: спочатку порожньо");
        w.fillEmpty(reg.available());
        check("t:wave".equals(w.get(0)) && "t:sit".equals(w.get(1)) && "t:dance".equals(w.get(2)) && w.get(3) == null && !w.configured(),
            "слоти: дефолтне заповнення порожніх");
        w.set(5, "t:wave");
        check("t:wave".equals(w.get(5)) && w.get(0) == null && w.indexOf("t:wave") == 5, "слоти: емоція в одному слоті (переїжджає)");
        w.set(1, "t:dance");
        check("t:dance".equals(w.get(1)) && "t:sit".equals(w.get(2)), "слоти: зайнята емоція міняється місцями");
        w.swap(1, 7);
        check(w.get(1) == null && "t:dance".equals(w.get(7)), "слоти: swap");
        w.clear(7);
        w.set(-1, "t:x");
        w.set(8, "t:x");
        check(w.get(7) == null && w.indexOf("t:x") < 0 && w.configured(), "слоти: clear і межі");
        w.set(3, "t:missing");
        check(w.resolve(3, reg) == null && w.resolve(5, reg) != null, "слоти: невідома емоція — порожній сегмент");

        String text = w.serialize();
        EmoteWheel w2 = new EmoteWheel();
        w2.parse(text + "\nбите\n9=t:x\nx=y\n6=t:wave\n");
        check(w2.ids().equals(w.ids()) && w2.configured(), "слоти: серіалізація переживає запис/читання, сміття пропущено");
        Path dir = Files.createTempDirectory("emotes");
        Path file = dir.resolve("sub/wheel.txt");
        w.save(file);
        EmoteWheel w3 = new EmoteWheel();
        check(w3.load(file) && w3.ids().equals(w.ids()), "слоти: збереження у файл і завантаження");
        check(!new EmoteWheel().load(dir.resolve("none.txt")), "слоти: немає файлу — false");
        Files.deleteIfExists(file);

        EmoteMessage m = EmoteMessage.play("t:wave");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        m.write(new DataOutputStream(bo));
        check(EmoteMessage.read(new DataInputStream(new ByteArrayInputStream(bo.toByteArray()))).equals(m), "емоції: повідомлення клієнт→сервер");

        EmoteSessions ses = new EmoteSessions();
        UUID p = UUID.randomUUID();
        ses.start(p, sit, 0, 64, 0, 0);
        check(ses.check(p, 0.4, 64, 0, true, 3) == EmoteSessions.Decision.KEEP && ses.active(p).x() == 0.4,
            "сесії: перші тіки — інерція після бігу не гасить, відлік їде за гравцем");
        check(ses.check(p, 0.5, 64, 0.05, true, 20) == EmoteSessions.Decision.KEEP, "сесії: дрібний поштовх не зупиняє");
        check(ses.check(p, 0.9, 64, 0, true, 20) == EmoteSessions.Decision.STOP_MOVED, "сесії: зрушив — сісти зупиняється");
        check(ses.check(p, 0.4, 64.5, 0, true, 20) == EmoteSessions.Decision.STOP_MOVED, "сесії: стрибок — теж рух");
        check(ses.check(p, 0.4, 64, 0, false, 2) == EmoteSessions.Decision.STOP_DENIED, "сесії: хук заборонив посеред емоції");
        ses.start(p, wave, 0, 64, 0, 0);
        check(ses.check(p, 5, 64, 0, true, 20) == EmoteSessions.Decision.KEEP, "сесії: upper_body — можна йти");
        check(ses.stop(p) != null && ses.active(p) == null && ses.check(p, 9, 9, 9, false, 20) == EmoteSessions.Decision.KEEP,
            "сесії: stop прибирає запис");

        ShaurmaEmotes.registry().clear();
        ShaurmaEmotes.register(wave);
        Object player = new Object();
        check(ShaurmaEmotes.allowedOnServer(player, wave) && ShaurmaEmotes.canOpenWheel(player), "фасад емоцій: за замовчуванням дозволено");
        ShaurmaEmotes.setServerGate((pl, e) -> pl != player);
        ShaurmaEmotes.setClientGate(pl -> { throw new RuntimeException(); });
        check(!ShaurmaEmotes.allowedOnServer(player, wave) && !ShaurmaEmotes.canOpenWheel(player), "фасад емоцій: хуки мода (і кривий хук = ні)");
        ShaurmaEmotes.setServerGate(null);
        ShaurmaEmotes.setClientGate(null);
        ShaurmaEmotes.setWheelEnabled(false);
        check(!ShaurmaEmotes.allowedOnServer(player, wave) && !ShaurmaEmotes.canOpenWheel(player), "фасад емоцій: колесо вимкнене модом");
        ShaurmaEmotes.setWheelEnabled(true);
        check(!ShaurmaEmotes.allowedOnServer(player, Emote.builder("t:x", "t:x").build()), "фасад емоцій: незареєстрована — ні");
        ShaurmaEmotes.registry().clear();

        AnimMessage lm = AnimMessage.play(p, AnimMessage.PlayRequestSpec.of("t:emote/sit").loop(AnimationClip.LoopMode.LOOP));
        bo = new ByteArrayOutputStream();
        lm.write(new DataOutputStream(bo));
        byte[] bytes = bo.toByteArray();
        check(AnimMessage.read(new DataInputStream(new ByteArrayInputStream(bytes))).equals(lm), "мережа: режим повтору емоції");
        AnimMessage old = AnimMessage.read(new DataInputStream(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, bytes.length - 1))));
        check(old.loop() == null && old.clip().equals("t:emote/sit"), "мережа: старе повідомлення без байта повтору читається");
    }

    static void network() throws Exception {
        UUID id = UUID.randomUUID();
        AnimMessage m = AnimMessage.play(id, AnimMessage.PlayRequestSpec.of("maniacmod:medkit_use").slot(Slot.ACTION)
            .mask("upper_body").fade(0.1f, 0.2f).startAt(0.75f).restart(true));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        m.write(new DataOutputStream(bo));
        AnimMessage r = AnimMessage.read(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())));
        check(r.equals(m), "мережа: повідомлення переживає запис/читання");
        check(AnimMessage.stopAll(id, 0.2f).op() == AnimMessage.Op.STOP_ALL, "мережа: stopAll");

        ShaurmaAnim.clips().replaceAll(java.util.Map.of(), java.util.Map.of());
        for (AnimationClip c : ClipParser.parse(LIB_JSON, "t", "lib", "player", WARN::add)) ShaurmaAnim.clips().register(c);
        UUID late = UUID.randomUUID();
        check(ShaurmaAnim.play(late, PlayRequest.of("t:a")), "фасад: play до появи сутності — відкладено");
        Animator a = ShaurmaAnim.animator(late, Rig.PLAYER);
        check(a.current(Slot.ACTION) != null, "фасад: відкладений play застосовано при створенні аніматора");
        ShaurmaAnim.retainOnly(List.of());
        check(ShaurmaAnim.find(late) == null, "фасад: retainOnly прибирає зниклі сутності");
    }

    /** Усі реальні файли маніяка мають розібратись без попереджень. */
    static void realFiles(Path resources) throws Exception {
        Path assets = resources.resolve("assets");
        AnimResourceLoader rl = new AnimResourceLoader(WARN::add);
        int before = WARN.size();
        try (Stream<Path> files = Files.walk(assets)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                Path rel = assets.relativize(p);
                if (rel.getNameCount() < 2) continue;
                String ns = rel.getName(0).toString();
                String path = rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/');
                if (!AnimResourceLoader.accepts(path)) continue;
                rl.accept(ns, path, Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        for (int i = before; i < WARN.size(); i++) System.out.println("  WARN " + WARN.get(i));
        check(WARN.size() == before, "реальні файли: " + rl.files() + " файлів, " + rl.clips().size() + " кліпів, "
            + rl.meshes().size() + " моделей — без попереджень");
        for (String id : List.of("maniacmod:test_maniac/idle", "maniacmod:test_maniac/walk", "maniacmod:test_maniac/attack",
                                 "maniacmod:test_maniac/carry", "maniacmod:downed_lie", "maniacmod:generator_repair",
                                 "maniacmod:carried_shoulder", "maniacmod:hook_hang", "maniacmod:rope_hang", "maniacmod:climb_rope",
                                 "maniacmod:wake_up", "maniacmod:wounded", "maniacmod:pick_up_item",
                                 "maniacmod:medkit_use", "maniacmod:adrenaline_use", "maniacmod:scissors_cut",
                                 "maniacmod:crowbar_hit", "maniacmod:flashlight_hold", "maniacmod:flashlight_hold_left",
                                 "maniacmod:emote_wave", "maniacmod:emote_point", "maniacmod:emote_follow_me", "maniacmod:emote_laugh",
                                 "maniacmod:emote_clap", "maniacmod:emote_shrug", "maniacmod:emote_cheer", "maniacmod:emote_sit",
                                 "maniacmod:emote_dance")) {
            check(rl.clips().containsKey(id), "реальні файли: є " + id);
        }
        check(rl.meshes().containsKey("maniacmod:props/bandage"), "реальні файли: модель пропса бинта");
        AnimationClip med = rl.clips().get("maniacmod:medkit_use");
        check(med != null && med.props().size() == 1 && "left_item".equals(med.props().get(0).parentBone())
            && med.defaults().firstPerson() == AnimationClip.FirstPerson.MODEL && "upper_body".equals(med.defaults().mask()),
            "аптечка: пропс у лівій руці, перша особа, маска upper_body");
        // ліхтарик цілиться за поглядом: query.head_x_rotation у кліпі
        ClipLibrary fl = new ClipLibrary();
        fl.register(rl.clips().get("maniacmod:flashlight_hold"));
        Animator fa = new Animator(UUID.randomUUID(), Rig.PLAYER, fl);
        fa.play(PlayRequest.of("maniacmod:flashlight_hold").fade(0, 0));
        fa.tick(null, null, null);
        Pose fo = new Pose(Rig.PLAYER);
        fa.inputs().headPitch = 0;
        fa.evaluate(1f, null, false, fo);
        float level0 = fo.bone("right_arm").rx;
        fa.inputs().headPitch = 40; // дивиться вниз
        fa.evaluate(1f, null, false, fo);
        check(fo.bone("right_arm").rx - level0 > 30 && !fo.touched("left_arm"), "ліхтарик: рука опускається за поглядом, ліва вільна");
        // кожен кліп семплиться без винятків на всьому проміжку
        boolean ok = true;
        for (AnimationClip c : rl.clips().values()) {
            Rig rig = c.rig().equals("player") ? Rig.PLAYER : Rig.fromHierarchy(c.rig(), new ArrayList<>(c.bones()),
                new ArrayList<>(java.util.Collections.nCopies(c.bones().size(), (String) null)), null);
            ClipLibrary lib = new ClipLibrary();
            lib.register(c);
            Animator a = new Animator(UUID.randomUUID(), rig, lib);
            a.play(PlayRequest.of(c.id()).fade(0, 0));
            Pose out = new Pose(rig);
            try {
                for (int i = 0; i < 40; i++) {
                    a.tick(null, null, null);
                    a.evaluate(0.5f, null, false, out);
                    for (int b = 0; b < rig.size(); b++) {
                        BoneTransform t = out.bone(b);
                        if (Float.isNaN(t.rx) || Float.isNaN(t.px) || Float.isNaN(t.sx)) { ok = false; System.out.println("  NaN in " + c.id()); }
                    }
                }
            } catch (RuntimeException e) {
                ok = false;
                System.out.println("  " + c.id() + ": " + e);
            }
        }
        check(ok, "реальні файли: усі кліпи семплуються без NaN/винятків");
    }
}
