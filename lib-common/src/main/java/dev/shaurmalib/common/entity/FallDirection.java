package dev.shaurmalib.common.entity;

/**
 * Узагальнений напрямок падіння/пози для {@code SkinnableEntityBase}-подібних
 * сутностей (план, п. 3.22) — узагальнення {@code DeathDirection}
 * snipers_shaurma. Перенесено як ЧИСТУ геометрію (кути/координати), без
 * {@code ServerPlayer}-параметра оригіналу — {@link #calcFrom} тут приймає
 * прості {@code double}/{@code float} значення (позиція жертви, її yaw,
 * позиція джерела атаки), щоб лишитись у {@code lib-common} без жодної
 * залежності від конкретного vanilla-типу гравця. Консюмер, що має
 * {@code ServerPlayer}, викликає це так:
 * <pre>{@code
 * FallDirection dir = FallDirection.calcFrom(
 *     victim.getX(), victim.getZ(), victim.getYRot(),
 *     source.getX(), source.getZ(), true);
 * }</pre>
 * <p>
 * Назва свідомо змінена з {@code DeathDirection} на {@link FallDirection} —
 * узагальнення не обмежується сценарієм "смерть": та сама геометрія
 * ("з якого боку відносно тіла прийшов імпульс → куди тіло падає")
 * застосовна до будь-якої skinnable-сутності, що падає під впливом
 * зовнішньої сили (майбутній консюмер може, наприклад, застосувати це до
 * оглушеного/нокаутованого, а не обов'язково мертвого, персонажа).
 */
public enum FallDirection {
    /** Падає вперед (обличчям вниз) — типово: удар прийшов ЗЗАДУ. */
    FRONT,
    /** Падає назад (спиною вниз) — типово: удар прийшов СПЕРЕДУ. */
    BACK,
    /** Немає визначеного джерела імпульсу (напр. смерть без кілера). */
    GENERIC;

    /**
     * Визначає напрямок падіння за позицією жертви (і її yaw) та позицією
     * джерела імпульсу — те саме обчислення, що {@code DeathDirection.calcFrom},
     * але без {@code ServerPlayer}-типу в сигнатурі.
     *
     * @param victimX/Z    позиція жертви в момент удару.
     * @param victimYawDeg yaw жертви (Minecraft-конвенція: 0°=південь(+Z),
     *                     -90°=схід(+X)).
     * @param sourceX/Z    позиція джерела імпульсу (кілер/вибух/тощо).
     * @param hasSource    {@code false} → одразу {@link #GENERIC} (немає
     *                     джерела, з яким рахувати кут).
     */
    public static FallDirection calcFrom(double victimX, double victimZ, float victimYawDeg,
                                          double sourceX, double sourceZ, boolean hasSource) {
        if (!hasSource) return GENERIC;

        double dx = sourceX - victimX;
        double dz = sourceZ - victimZ;
        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) return GENERIC;

        // Кут атаки у world-coords: 0°=північ(-Z), 90°=схід(+X), 180°=південь(+Z), 270°=захід(-X)
        double worldAngle = Math.toDegrees(Math.atan2(dx, -dz));
        if (worldAngle < 0) worldAngle += 360;

        // Напрямок погляду жертви у тій самій world-coords системі.
        double worldForward = (victimYawDeg + 180) % 360;
        if (worldForward < 0) worldForward += 360;

        // Відносний кут: 0° = імпульс прийшов спереду (куди дивиться жертва).
        double relAngle = ((worldAngle - worldForward) % 360 + 360) % 360;

        // relAngle ~0° = удар спереду → тіло падає НАЗАД → BACK.
        // relAngle ~180° = удар ззаду (чи збоку) → тіло падає ВПЕРЕД → FRONT.
        if (relAngle >= 315 || relAngle < 45) {
            return BACK;
        } else {
            return FRONT;
        }
    }

    /** {@link #GENERIC} → {@link #FRONT} (найприродніша поза "без визначеного кілера"). Решта — себе. */
    public FallDirection resolveRandom() {
        return this == GENERIC ? FRONT : this;
    }
}
