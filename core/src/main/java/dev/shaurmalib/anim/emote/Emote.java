package dev.shaurmalib.anim.emote;

import java.util.Objects;

/**
 * Опис емоції: що показати в колесі і як її грати. Кліп — звичайний кліп рушія
 * ({@code assets/<ns>/shaurma_anim/**.json}); тут лише поведінка «як емоції».
 *
 * <pre>
 * ShaurmaEmotes.register(Emote.builder("maniacmod:wave", "maniacmod:emote/wave").name("Помахати")
 *     .category("social").upperBody().build());
 * ShaurmaEmotes.register(Emote.builder("maniacmod:sit", "maniacmod:emote/sit").name("Сісти")
 *     .fullBody().loop(true).build());                  // рух — встає
 * </pre>
 *
 * <ul>
 *   <li>{@code mask} — {@code upper_body}: ноги веде локомоція, можна йти; {@code full}: усе тіло;</li>
 *   <li>{@code loop} — null: як у файлі кліпа; true/false — перекрити;</li>
 *   <li>{@code interruptOnMove} — зрушив з місця → емоція гасне (за замовчуванням для {@code full}).</li>
 * </ul>
 */
public final class Emote {

    public static final String MASK_FULL = "full";
    public static final String MASK_UPPER = "upper_body";

    private final String id, name, clip, icon, category, mask;
    private final Boolean loop;
    private final boolean interruptOnMove;

    private Emote(Builder b) {
        this.id = b.id;
        this.clip = b.clip;
        this.name = b.name == null ? b.id : b.name;
        this.icon = b.icon;
        this.category = b.category == null ? "general" : b.category;
        this.mask = b.mask;
        this.loop = b.loop;
        this.interruptOnMove = b.interruptOnMove != null ? b.interruptOnMove : !canMove(b.mask);
    }

    public static Builder builder(String id, String clip) { return new Builder(id, clip); }

    public String id() { return id; }
    /** Назва за замовчуванням; версія гри показує переклад {@code emote.<ns>.<шлях>}, якщо він є. */
    public String name() { return name; }
    public String clip() { return clip; }
    /** Текстура іконки {@code ns:textures/...png} або null (тоді в колесі лише назва). */
    public String icon() { return icon; }
    public String category() { return category; }
    public String mask() { return mask; }
    public Boolean loop() { return loop; }
    public boolean interruptOnMove() { return interruptOnMove; }
    /** Чи можна йти, поки емоція грає (маска не забирає ноги). */
    public boolean canMove() { return canMove(mask); }

    private static boolean canMove(String mask) { return !MASK_FULL.equals(mask); }

    @Override public boolean equals(Object o) { return o instanceof Emote e && e.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "Emote[" + id + " → " + clip + ", " + mask + "]"; }

    public static final class Builder {
        private final String id, clip;
        private String name, icon, category, mask = MASK_UPPER;
        private Boolean loop, interruptOnMove;

        private Builder(String id, String clip) {
            this.id = Objects.requireNonNull(id, "id");
            this.clip = Objects.requireNonNull(clip, "clip");
        }

        public Builder name(String n) { name = n; return this; }
        public Builder icon(String i) { icon = i; return this; }
        public Builder category(String c) { category = c; return this; }
        /** Маска рушія: {@code full}, {@code upper_body} або будь-яка інша специфікація. */
        public Builder mask(String m) { mask = m == null || m.isBlank() ? MASK_UPPER : m; return this; }
        public Builder fullBody() { return mask(MASK_FULL); }
        public Builder upperBody() { return mask(MASK_UPPER); }
        public Builder loop(boolean l) { loop = l; return this; }
        public Builder interruptOnMove(boolean b) { interruptOnMove = b; return this; }
        public Emote build() { return new Emote(this); }
    }
}
