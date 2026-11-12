package dev.shaurmalib.anim.mc;

import dev.shaurmalib.anim.pose.BoneTransform;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.Rig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Рушій ShaurmaLib для GeckoLib-моделей (маньяк, моби): кістки ведуться позою рушія КОЖЕН КАДР
 * (з {@code partialTick}), а не контролерами GeckoLib на ігровому тіку — тож рух плавний, а шари,
 * маски, погляд і правила ті самі, що для ванільного гравця.
 *
 * <h2>Як підключити до свого GeoRenderer</h2>
 * <pre>
 * // перед рендером моделі:
 * pose = GeoAnimBridge.evaluate(player, rig, partialTick);
 * // у renderRecursively(bone):
 * GeoAnimBridge.Saved s = GeoAnimBridge.apply(bone, pose);
 * try { super.renderRecursively(...); } finally { GeoAnimBridge.restore(bone, s); }
 * </pre>
 * Контролери GeckoLib для цієї моделі треба прибрати (інакше два рушії сперечаються за кістки).
 *
 * Конвенції GeckoLib (з його {@code AnimationController}): обертання кліпа {@code (−x, −y, z)} у радіанах
 * поверх початкового знімка, зсув — як у файлі, масштаб — як у файлі.
 */
public final class GeoAnimBridge {

    private GeoAnimBridge() {}

    private static final Map<BakedGeoModel, Rig> RIGS = new WeakHashMap<>();

    /** Риг з baked-моделі GeckoLib (кешується на модель; після F3+T модель нова — риг теж). */
    public static Rig rig(BakedGeoModel model, String rigName) {
        Rig r = RIGS.get(model);
        if (r != null && r.name().equals(rigName)) return r;
        List<String> names = new ArrayList<>(), parents = new ArrayList<>();
        List<float[]> pivots = new ArrayList<>();
        for (GeoBone b : model.topLevelBones()) collect(b, null, names, parents, pivots);
        r = Rig.fromHierarchy(rigName, names, parents, pivots);
        RIGS.put(model, r);
        return r;
    }

    private static void collect(GeoBone b, String parent, List<String> names, List<String> parents, List<float[]> pivots) {
        names.add(b.getName());
        parents.add(parent);
        pivots.add(new float[] { b.getPivotX(), b.getPivotY(), b.getPivotZ() });
        for (GeoBone c : b.getChildBones()) collect(c, b.getName(), names, parents, pivots);
    }

    /** Поза кадру для geo-сутності (база — спокій моделі, погляд процедурний). */
    public static Pose evaluate(LivingEntity e, Rig rig, float partial) {
        if (rig == null) return null;
        dev.shaurmalib.anim.runtime.Animator a = dev.shaurmalib.anim.ShaurmaAnim.animator(e.getUUID(), rig);
        McAnim.fillFrameInputs(e, a.inputs(), partial);
        return McAnim.evaluateWith(e, a, partial, null, false);
    }

    /** Збережений стан кістки, щоб повернути baked-модель як була (вона спільна для всіх сутностей і Tab). */
    public record Saved(float rx, float ry, float rz, float px, float py, float pz, float sx, float sy, float sz) {}

    /** @return знімок для {@link #restore} або null, якщо рушій цю кістку не чіпав. */
    public static Saved apply(GeoBone bone, Pose pose) {
        if (pose == null) return null;
        int idx = pose.rig().indexOf(bone.getName());
        if (idx < 0 || !pose.touched(idx)) return null;
        Saved s = new Saved(bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getPosX(), bone.getPosY(), bone.getPosZ(),
            bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        BoneTransform b = pose.bone(idx);
        BoneSnapshot init = bone.getInitialSnapshot();
        bone.setRotX(init.getRotX() - b.rx * Mth.DEG_TO_RAD);
        bone.setRotY(init.getRotY() - b.ry * Mth.DEG_TO_RAD);
        bone.setRotZ(init.getRotZ() + b.rz * Mth.DEG_TO_RAD);
        bone.setPosX(b.px);
        bone.setPosY(b.py);
        bone.setPosZ(b.pz);
        bone.setScaleX(b.sx);
        bone.setScaleY(b.sy);
        bone.setScaleZ(b.sz);
        return s;
    }

    public static void restore(GeoBone bone, Saved s) {
        if (s == null) return;
        bone.setRotX(s.rx());
        bone.setRotY(s.ry());
        bone.setRotZ(s.rz());
        bone.setPosX(s.px());
        bone.setPosY(s.py());
        bone.setPosZ(s.pz());
        bone.setScaleX(s.sx());
        bone.setScaleY(s.sy());
        bone.setScaleZ(s.sz());
    }
}
