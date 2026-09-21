package dev.shaurmalib.common.playeranim;

import java.util.Map;
import java.util.Optional;

/**
 * Опис додаткової правки кісток поверх активної пози — без PAL-типів.
 * Forge-шар перетворює її на {@code AdjustmentModifier}.
 * <p>
 * <b>Потрібна лише для значень, що залежать від стану гри</b> (напр. нахил
 * рук за поточним {@code player.getXRot()}), які не можна намалювати
 * статичним keyframe-файлом. Статичний нахил тіла під час ремонту
 * художник просто малює в {@code .json} на кістці {@code body}/{@code torso}
 * — тоді цей клас не використовується взагалі.
 * <p>
 * Правка <b>додається</b> до значення нижніх шарів (на відміну від
 * keyframe-кістки, яка <i>замінює</i>), а кістка, для якої провайдер
 * повертає {@link Optional#empty()}, лишається прозорою — це і є "маска".
 * <p>
 * Імена кісток PlayerAnimator: {@code head}, {@code torso} (це ванільна
 * {@code body}), {@code rightArm}, {@code leftArm}, {@code rightLeg},
 * {@code leftLeg}, а також синтетична {@code body}, що рухає <i>все</i>
 * тіло одразу (для нахилу лише корпуса — {@code torso}).
 */
public final class BoneAdjustment {

    private BoneAdjustment() {}

    /**
     * Правка однієї кістки.
     *
     * @param rotX rotY rotZ поворот у <b>радіанах</b>, додається до поточного
     * @param offX offY offZ зсув у <b>пікселях моделі</b> (одиниці {@code ModelPart},
     *                       1/16 блока), додається до поточного
     */
    public record Part(float rotX, float rotY, float rotZ, float offX, float offY, float offZ) {

        public static final Part NONE = new Part(0, 0, 0, 0, 0, 0);

        public static Part rotation(float rotX, float rotY, float rotZ) {
            return new Part(rotX, rotY, rotZ, 0, 0, 0);
        }

        public static Part offset(float offX, float offY, float offZ) {
            return new Part(0, 0, 0, offX, offY, offZ);
        }

        /** Поворот навколо X у градусах — зручно для нахилу/пітчу. */
        public static Part pitchDegrees(float degrees) {
            return rotation((float) Math.toRadians(degrees), 0, 0);
        }
    }

    /**
     * Постачальник правок. Викликається <b>щокадру для кожної кістки</b>
     * (тому має бути дешевим і не аллокувати зайвого) і може повертати різні
     * значення від кадру до кадру. Бібліотека сама відкидає нескінченні/NaN
     * значення й ловить винятки — але швидкодію мусить забезпечити консюмер.
     */
    @FunctionalInterface
    public interface Provider {
        Optional<Part> partFor(String boneName);
    }

    /** Статична мапа {@code bone → Part}; кістки, яких немає в мапі, лишаються прозорими. */
    public static Provider ofMap(Map<String, Part> parts) {
        Map<String, Part> copy = Map.copyOf(parts);
        return bone -> Optional.ofNullable(copy.get(bone));
    }

    /** Одна кістка — одна правка; решта прозорі. */
    public static Provider ofBone(String bone, Part part) {
        return b -> bone.equals(b) ? Optional.of(part) : Optional.empty();
    }
}
