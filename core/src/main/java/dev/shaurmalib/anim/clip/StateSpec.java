package dev.shaurmalib.anim.clip;

/**
 * Кліп-«стан» (тримає ліхтарик, каністру): поза НАКЛАДАЄТЬСЯ на рух, а не заміняє його.
 *
 * Для кожної кістки, яку анімує стан: {@code результат = поза_стану + сила × рух_знизу}, де «рух знизу» —
 * те, що дали нижчі шари (ванільна поза або кліпи локомоції: змах рук при бігу, нахил на шифті, дихання idle).
 * Сила залежить від того, що робить гравець: стоїть ({@link #followIdle}), іде/біжить ({@link #followMove},
 * плавно за швидкістю кроку), крадеться ({@link #followSneak}).
 *
 * <pre>
 * "state": { "follow_move": 0.35, "follow_sneak": 1, "follow_idle": 1, "yield_swing": true, "yield_use": true }
 * </pre>
 *
 * @param yieldSwing під час удару поза плавно поступається ванільній руці (змах разом з предметом)
 * @param yieldUse   під час використання предмета (пити, їсти, лук, щит) — ванільна рука
 */
public record StateSpec(float followMove, float followSneak, float followIdle, boolean yieldSwing, boolean yieldUse) {

    public static final StateSpec DEFAULT = new StateSpec(0.35f, 1f, 1f, true, true);

    /** Сила «йти за рухом» зараз. */
    public float follow(boolean sneaking, float moveAmount) {
        if (sneaking) return followSneak;
        float m = moveAmount < 0 ? 0 : (moveAmount > 1 ? 1 : moveAmount);
        return followIdle + (followMove - followIdle) * m;
    }
}
