package dev.shaurmalib.anim.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.shaurmalib.anim.pose.BoneTransform;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.Rig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/**
 * Переклад пози рушія ↔ ванільна {@link HumanoidModel}.
 *
 * <h2>Простори (перевірено проти GeckoLib і Blockbench)</h2>
 * Кліп зберігає значення як у Blockbench. Сцена Blockbench має Y вгору; {@code ModelPart} — Y вниз і
 * дзеркальний X, тобто сцена, повернута на 180° навколо Z. Звідси для ванільної моделі:
 * <pre>
 *   обертання (x, y, z)°  →  xRot, yRot, zRot += rad(x, y, z)      (порядок ZYX, як у ModelPart)
 *   зсув      (x, y, z)px →  x += x;  y −= y;  z += z
 * </pre>
 * Півоти ригу Blockbench (Y вгору від ступнів) ↔ ванільні: точка сцени (x,y,z) = ModelPart (−x, 24−y, z).
 * Якір предмета в руці (кістка {@code right_item}, півот (6,12,−2)) збігається з ванільним
 * {@code ItemInHandLayer} точно: (−1, 10, −2) у просторі руки — тому предмет у кліпі стоїть там, де в Blockbench.
 */
public final class PlayerModelBridge {

    private PlayerModelBridge() {}

    private static final Rig R = Rig.PLAYER;
    static final int HEAD = R.indexOf("head"), TORSO = R.indexOf("torso"), RIGHT_ARM = R.indexOf("right_arm"),
        LEFT_ARM = R.indexOf("left_arm"), RIGHT_LEG = R.indexOf("right_leg"), LEFT_LEG = R.indexOf("left_leg"),
        ROOT = R.indexOf("body"), RIGHT_ITEM = R.indexOf("right_item"), LEFT_ITEM = R.indexOf("left_item");

    /** Півот кореня (стегна) у просторі ModelPart, пікселі. */
    private static final float ROOT_PIVOT_Y = 24f - 12f;

    static ModelPart part(HumanoidModel<?> m, int bone) {
        if (bone == HEAD) return m.head;
        if (bone == TORSO) return m.body;
        if (bone == RIGHT_ARM) return m.rightArm;
        if (bone == LEFT_ARM) return m.leftArm;
        if (bone == RIGHT_LEG) return m.rightLeg;
        if (bone == LEFT_LEG) return m.leftLeg;
        return null;
    }

    private static final int[] PARTS = { HEAD, TORSO, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG };

    /** Ванільна поза моделі → поза рушія (база під кліпами). */
    public static void capture(HumanoidModel<?> m, Pose base) {
        base.reset();
        for (int bone : PARTS) {
            ModelPart p = part(m, bone);
            PartPose rest = p.getInitialPose();
            BoneTransform b = base.bone(bone);
            b.rx = (p.xRot - rest.xRot) * Mth.RAD_TO_DEG;
            b.ry = (p.yRot - rest.yRot) * Mth.RAD_TO_DEG;
            b.rz = (p.zRot - rest.zRot) * Mth.RAD_TO_DEG;
            b.px = p.x - rest.x;
            b.py = -(p.y - rest.y);
            b.pz = p.z - rest.z;
            b.sx = p.xScale;
            b.sy = p.yScale;
            b.sz = p.zScale;
        }
    }

    /** Пише в модель ЛИШЕ кістки, які змінив рушій. @return чи щось змінено. */
    public static boolean apply(HumanoidModel<?> m, Pose pose) {
        boolean any = false;
        for (int bone : PARTS) {
            if (!pose.touched(bone)) continue;
            write(part(m, bone), pose.bone(bone));
            any = true;
        }
        if (any && m instanceof PlayerModel<?> pm) syncOverlays(pm);
        else if (any) m.hat.copyFrom(m.head);
        return any;
    }

    /** Лише руки (перша особа). */
    public static void applyArms(HumanoidModel<?> m, Pose pose) {
        if (pose.touched(RIGHT_ARM)) write(m.rightArm, pose.bone(RIGHT_ARM));
        if (pose.touched(LEFT_ARM)) write(m.leftArm, pose.bone(LEFT_ARM));
        if (m instanceof PlayerModel<?> pm) syncOverlays(pm);
    }

    private static void write(ModelPart p, BoneTransform b) {
        PartPose rest = p.getInitialPose();
        p.xRot = rest.xRot + b.rx * Mth.DEG_TO_RAD;
        p.yRot = rest.yRot + b.ry * Mth.DEG_TO_RAD;
        p.zRot = rest.zRot + b.rz * Mth.DEG_TO_RAD;
        p.x = rest.x + b.px;
        p.y = rest.y - b.py;
        p.z = rest.z + b.pz;
        p.xScale = b.sx;
        p.yScale = b.sy;
        p.zScale = b.sz;
    }

    static void syncOverlays(PlayerModel<?> m) {
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
    }

    /** Повертає частини в позу спокою (на початку setupAnim, якщо попередній кадр їх зсував). */
    public static void reset(HumanoidModel<?> m) {
        for (int bone : PARTS) part(m, bone).resetPose();
        m.hat.resetPose();
        if (m instanceof PlayerModel<?> pm) {
            pm.jacket.resetPose();
            pm.leftSleeve.resetPose();
            pm.rightSleeve.resetPose();
            pm.leftPants.resetPose();
            pm.rightPants.resetPose();
        }
    }

    /** Корінь {@code body} (усе тіло: лягти, нахилитись, бути піднятим) — у просторі моделі перед частинами. */
    public static void applyRoot(PoseStack ps, Pose pose) {
        if (pose == null || ROOT < 0 || !pose.touched(ROOT)) return;
        BoneTransform b = pose.bone(ROOT);
        ps.translate(b.px / 16f, -b.py / 16f, b.pz / 16f);
        ps.translate(0, ROOT_PIVOT_Y / 16f, 0);
        if (b.rx != 0 || b.ry != 0 || b.rz != 0) {
            ps.mulPose(new Quaternionf().rotationZYX(b.rz * Mth.DEG_TO_RAD, b.ry * Mth.DEG_TO_RAD, b.rx * Mth.DEG_TO_RAD));
        }
        if (b.sx != 1 || b.sy != 1 || b.sz != 1) ps.scale(safe(b.sx), safe(b.sy), safe(b.sz));
        ps.translate(0, -ROOT_PIVOT_Y / 16f, 0);
    }

    /**
     * Кістка предмета, викликати там, де ванільний {@code ItemInHandLayer} уже поставив стек у якір
     * (після {@code XP(-90)·YP(180)·translate}). Перетворення задане в осях руки, тож обертаємо назад,
     * застосовуємо і повертаємо ванільну орієнтацію: {@code R⁻¹·B·R}.
     */
    public static void applyItemBone(PoseStack ps, Pose pose, HumanoidArm arm) {
        int bone = arm == HumanoidArm.RIGHT ? RIGHT_ITEM : LEFT_ITEM;
        if (pose == null || !pose.touched(bone)) return;
        BoneTransform b = pose.bone(bone);
        ps.mulPose(Axis.YP.rotationDegrees(-180f));
        ps.mulPose(Axis.XP.rotationDegrees(90f));
        applyLocal(ps, b);
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
    }

    /** Трансформ кістки в осях ModelPart навколо поточного початку стеку. */
    static void applyLocal(PoseStack ps, BoneTransform b) {
        ps.translate(b.px / 16f, -b.py / 16f, b.pz / 16f);
        if (b.rx != 0 || b.ry != 0 || b.rz != 0) {
            ps.mulPose(new Quaternionf().rotationZYX(b.rz * Mth.DEG_TO_RAD, b.ry * Mth.DEG_TO_RAD, b.rx * Mth.DEG_TO_RAD));
        }
        if (b.sx != 1 || b.sy != 1 || b.sz != 1) ps.scale(safe(b.sx), safe(b.sy), safe(b.sz));
    }

    /** Якір предмета в руці, простір ModelPart (пікселі). */
    static float[] itemAnchor(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? new float[] { -1, 10, -2 } : new float[] { 1, 10, -2 };
    }

    static float safe(float s) { return Math.abs(s) < 1e-4f ? 1e-4f : s; }
}
