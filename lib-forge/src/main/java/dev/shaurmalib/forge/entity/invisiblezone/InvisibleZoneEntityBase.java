package dev.shaurmalib.forge.entity.invisiblezone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Базовий клас "сутність-зона без хітбоксу" (план, п. 3.28) — узагальнення
 * {@code SCNDomeShellEntity} snipers_shaurma. Перенесено як
 * <b>найризикованіший</b> модуль плану, останнім з важких систем (Етап 4,
 * крок 31), з окремим regression-чек-листом (див. клас-докстрінг нижче).
 * <p>
 * Оригінал вирішував 4 незалежні, задокументовані в коді проблеми рушія
 * Minecraft одночасно. Ця база інкапсулює всі чотири "з коробки", щоб
 * жоден майбутній консюмер (snipers, maniac-mode) не проходив через той
 * самий процес налагодження вдруге:
 * <ol>
 *   <li><b>{@code setBoundingBox()} — final у {@code Entity}.</b>
 *       Ванільний {@code Entity.tick()} викликає {@code setPos()} →
 *       {@code setBoundingBox(makeBoundingBox())}, що скидає AABB назад у
 *       координати типу сутності (0-розмір). Тому {@link #tick()} тут
 *       <b>НЕ</b> викликає {@code super.tick()} — AABB підтримується
 *       вручну через {@link #applyAABB()}, викликаний і в {@link #init}, і
 *       щотік для sanity-re-apply.</li>
 *   <li><b>{@code xOld}/{@code yOld}/{@code zOld} ніколи не оновлюються</b>
 *       без {@code super.tick()}. Сторонні моди снарядів (типовий приклад
 *       — TACZ через {@code HitboxHelper.getFixedBoundingBox()}) рахують
 *       "швидкість" сутності як {@code (pos - xOld)} для лаг-компенсації
 *       рейкасту; якщо ці поля розсинхронізуються — AABB рейкасту
 *       з'їжджає від видимої позиції. Тут вони явно синхронізуються з
 *       поточною (нерухомою) позицією і в {@link #init}, і щотік, і після
 *       {@link #readAdditionalSaveData} (чанк-релоад) — завжди дельта
 *       {@code 0}.</li>
 *   <li><b>Опційний {@code ITargetEntity}-міст</b> (TACZ) —
 *       {@code lib-forge} НЕ залежить від TACZ напряму (жоден
 *       {@code compileOnly}/{@code implementation} у {@code build.gradle},
 *       план розділ 4 п. 3), тому ця база не реалізує {@code ITargetEntity}
 *       сама. Замість цього консюмер, у якого TACZ присутній, підключає
 *       {@link ExternalHitBridge} — той самий інверсія-залежності патерн,
 *       що вже застосований у {@code GunDamageBridge} (план 3.26): бібліотека
 *       не знає конкретного стороннього типу, лише викликає callback, якщо
 *       його зареєстровано. Якщо TACZ відсутній (наприклад у maniac-mode) —
 *       міст просто не підключається, сутність поводиться як звичайна
 *       invisible-collision-zone для ванільних снарядів/гравців.</li>
 *   <li><b>Анізотропний AABB</b> — окремі напівосі halfX/halfY/halfZ (не
 *       єдиний ізотропний halfSize), щоб зона могла мати форму, зорієнтовану
 *       по дотичній площині (широкий тонкий патч), а не однаковий куб для
 *       кожного сегмента.</li>
 * </ol>
 * <p>
 * <b>Що лишається консюмеру:</b> лише опис форми/позиції/тривалості життя
 * через {@link ZoneShapeDescriptor} в {@link #init}, і (опційно) підписка
 * {@link ExternalHitBridge}. Все інше — invulnerable, no-gravity, silent,
 * {@code interact() → PASS}, NBT save/load з backward-compat на старий
 * ізотропний формат ({@code half_size}) — дефолти базового класу.
 * <p>
 * <b>REGRESSION-ЧЕКЛИСТ перед переносом конкретного консюмера</b>
 * (план, п. 3.28 і п. 42 Етапу 6 — робити на тестовій гілці, не напряму
 * в робочій):
 * <ul>
 *   <li>спавн зони і стрільба по ній звичайною/ванільною зброєю;</li>
 *   <li>стрільба по ній зброєю стороннього мода (якщо {@link ExternalHitBridge}
 *       підключено) — перевірити стабільність влучання (не "то працює, то ні");</li>
 *   <li>та сама перевірка БЕЗ підключеного {@link ExternalHitBridge} —
 *       переконатись, що зона все ще коректно блокує ванільні снаряди;</li>
 *   <li>чанк-релоад під час існування зони (вивантажити й повернути чанк) —
 *       перевірити що AABB і xOld/yOld/zOld лишились коректними після
 *       {@link #readAdditionalSaveData};</li>
 *   <li>паралельне існування кількох зон одночасно (не плутають одна одну);</li>
 *   <li>природне видалення за {@code maxLife} і примусове видалення ззовні
 *       (наприклад, дочасне зняття) — обидва шляхи мають коректно прибирати
 *       сутність без "зависань".</li>
 * </ul>
 */
public abstract class InvisibleZoneEntityBase extends Entity {

    /** Тег власника (напр. id купола/зони-батька) — лише мітка для групового
     *  видалення ззовні, НІКОЛИ не використовується для лукапу щотік. */
    private String ownerTag = "";
    private String teamId = "";
    private int lifeTicks = 0;
    private int maxLife = 400;

    private double posX, posY, posZ;
    private float halfX = 0.5f, halfY = 0.5f, halfZ = 0.5f;

    /** Центр батьківської структури на момент спавну — статичне число,
     *  скопійоване один раз, не "живий" лукап. Використовується підкласами
     *  для розрахунків на кшталт нормалі рикошету; сама база його не читає. */
    private double originX, originY, originZ;

    /** Опційний міст до стороннього не-{@code LivingHurtEvent} джерела
     *  урону (TACZ і подібні) — {@code null}, якщо консюмер його не підключив. */
    private ExternalHitBridge externalHitBridge;

    protected InvisibleZoneEntityBase(EntityType<? extends InvisibleZoneEntityBase> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvisible(true);
        // isPickable()=true нижче — TACZ-подібні моди будують список
        // кандидатів власним AABB-запитом, не колізіями рушія, тому
        // noPhysics тут не вирішальний для них. Лишаємо canCollideWith=true/
        // noPhysics=false, щоб сутність коректно трактувалась суцільною
        // перепоною для ванільних снарядів і ThrowableItemProjectile.
        this.noPhysics = false;
        this.blocksBuilding = true;
    }

    /**
     * Ініціалізація незалежної сутності — власна абсолютна позиція і форма,
     * встановлюються один раз. {@code ownerTag} лишається лише міткою для
     * групового видалення ззовні (консюмер сам відстежує, кому належить
     * зона), сутність його більше ніколи не читає й не шукає власника щотік.
     */
    public final void init(String ownerTag, String teamId, int maxLife, ZoneShapeDescriptor shape) {
        this.ownerTag = ownerTag == null ? "" : ownerTag;
        this.teamId = teamId == null ? "" : teamId;
        this.maxLife = maxLife;
        this.posX = shape.originX();
        this.posY = shape.originY();
        this.posZ = shape.originZ();
        this.halfX = shape.halfX();
        this.halfY = shape.halfY();
        this.halfZ = shape.halfZ();
        this.originX = shape.parentOriginX();
        this.originY = shape.parentOriginY();
        this.originZ = shape.parentOriginZ();
        applyAABB();
        // ФІКС: xOld/yOld/zOld мають дорівнювати реальній позиції одразу,
        // а не лише з наступного tick() — інакше перший тік уже матиме
        // штучну "швидкість" від (0,0,0), і сторонній лаг-компенсатор
        // одразу зсуне AABB рейкасту на спавні. Див. клас-докстрінг п.2.
        this.xOld = posX;
        this.yOld = posY;
        this.zOld = posZ;
        onInit();
    }

    /** Хук для підкласів — викликається наприкінці {@link #init}, до
     *  жодного tick(). Типове використання: додаткова NBT-специфіка. */
    protected void onInit() {
    }

    /**
     * Підключає опційний міст до стороннього джерела урону, що обходить
     * {@code LivingHurtEvent} (план 3.26/3.28 — той самий інверсія-
     * залежності патерн, що {@code GunDamageBridge}). Консюмер викликає це
     * з власного TACZ-специфічного (чи еквівалентного) обробника, коли
     * TACZ дійсно присутній у classpath — якщо не викликати взагалі, зона
     * поводиться як звичайна invisible-collision-zone.
     */
    public final void bindExternalHitBridge(ExternalHitBridge bridge) {
        this.externalHitBridge = bridge;
    }

    /**
     * Викликається консюмером зі свого стороннього (напр. TACZ)
     * event-хука, коли той хук підтвердив влучання по цій зоні через
     * власний офіційний гачок стороннього мода (напр. {@code ITargetEntity}).
     * Бібліотека сама НЕ підписується на жоден сторонній тип події — це
     * робить консюмер, передаючи вже готові примітиви.
     */
    public final void notifyExternalHit(Entity projectile, DamageSource source, float damage) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (externalHitBridge != null) {
            externalHitBridge.onExternalHit(this, projectile, source, damage);
        }
    }

    public final String ownerTag() {
        return ownerTag;
    }

    public final String teamId() {
        return teamId;
    }

    public final double originX() {
        return originX;
    }

    public final double originY() {
        return originY;
    }

    public final double originZ() {
        return originZ;
    }

    @Override
    public final void tick() {
        // НЕ викликаємо super.tick() — він скидає AABB через makeBoundingBox().
        // Див. клас-докстрінг п.1.
        if (!level().isClientSide) {
            if (++lifeTicks >= maxLife) {
                discard();
                return;
            }
        }
        // AABB вже правильний і незмінний з моменту init() — sanity re-apply
        // на випадок якщо щось у двигуні його скинуло.
        applyAABB();

        // ФІКС: без super.tick() xOld/yOld/zOld (успадковані з Entity)
        // ніколи не оновлюються рушієм і можуть лишитись у довільному
        // стані з моменту конструктора/read NBT. Явно тримаємо їх рівними
        // поточній позиції щотік, щоб дельта завжди була 0 — сутність
        // нерухома, тому це коректно. Див. клас-докстрінг п.2.
        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();

        onZoneTick();
    }

    /** Хук для підкласів — викликається щотік ПІСЛЯ sanity-re-apply AABB
     *  і синхронізації xOld/yOld/zOld, до жодних інших змін стану. */
    protected void onZoneTick() {
    }

    private void applyAABB() {
        // setBoundingBox — final в Entity, але викликати можна — просто не override.
        this.setBoundingBox(new AABB(
                posX - halfX, posY - halfY, posZ - halfZ,
                posX + halfX, posY + halfY, posZ + halfZ
        ));
    }

    /**
     * Публічний sanity-виклик ззовні — примусово переставляє AABB одразу
     * після {@code addFreshEntity()}, на випадок якщо чанк-трекер встиг
     * закешувати дефолтний AABB типу сутності замість shape-AABB.
     */
    public final void reapplyAABB() {
        applyAABB();
    }

    @Override public final boolean isPickable() { return true; }
    @Override public final boolean isPushable() { return false; }

    /**
     * Явно дозволяємо участь у колізійних перевірках: багато шляхів
     * рейкасту (в т.ч. {@code ProjectileUtil.getEntityHitResult}) фільтрують
     * кандидатів через canCollideWith/collision-прапори. {@code isPickable()
     * = true} саме по собі недостатньо, якщо сутність водночас no-physics.
     */
    @Override
    public boolean canCollideWith(Entity entity) {
        return true;
    }

    /**
     * Безсмертна за замовчуванням: зона — лише геометричний hitbox.
     * Сторонні джерела урону гасяться консюмером вручну через
     * {@link #notifyExternalHit}; ванільний шлях тут навмисно завжди
     * заблокований, щоб зона не зникала від випадкового урону.
     */
    @Override public final boolean isInvulnerableTo(DamageSource source) { return true; }

    @Override
    public final boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** Без гравітації завжди — незалежно від зовнішніх модифікацій прапора. */
    @Override public final boolean isNoGravity() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("owner_tag", ownerTag);
        tag.putString("team_id", teamId);
        tag.putInt("life", lifeTicks);
        tag.putInt("max_life", maxLife);
        tag.putDouble("pos_x", posX);
        tag.putDouble("pos_y", posY);
        tag.putDouble("pos_z", posZ);
        tag.putFloat("half_x", halfX);
        tag.putFloat("half_y", halfY);
        tag.putFloat("half_z", halfZ);
        tag.putDouble("origin_x", originX);
        tag.putDouble("origin_y", originY);
        tag.putDouble("origin_z", originZ);
        onAddAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        ownerTag = tag.getString("owner_tag");
        teamId = tag.getString("team_id");
        lifeTicks = tag.getInt("life");
        maxLife = tag.getInt("max_life");
        posX = tag.getDouble("pos_x");
        posY = tag.getDouble("pos_y");
        posZ = tag.getDouble("pos_z");
        // Зворотна сумісність зі старим форматом (єдине ізотропне поле
        // half_size): якщо нових anisotropic-полів немає, розкладаємо
        // старий розмір по всіх трьох осях однаково.
        if (tag.contains("half_x")) {
            halfX = tag.getFloat("half_x");
            halfY = tag.getFloat("half_y");
            halfZ = tag.getFloat("half_z");
        } else {
            float legacy = tag.getFloat("half_size");
            halfX = halfY = halfZ = legacy > 0 ? legacy : 0.5f;
        }
        originX = tag.getDouble("origin_x");
        originY = tag.getDouble("origin_y");
        originZ = tag.getDouble("origin_z");
        applyAABB();
        // Той самий фікс, що й в init(): після читання NBT (наприклад
        // чанк-релоад) xOld мусить одразу дорівнювати збереженій позиції.
        this.xOld = posX;
        this.yOld = posY;
        this.zOld = posZ;
        onReadAdditionalSaveData(tag);
    }

    /** Хук для підкласів — додаткові поля власного стану (наприклад тип
     *  ефекту зони, kit-специфічні дані). Викликається після базових полів. */
    protected void onAddAdditionalSaveData(CompoundTag tag) {
    }

    /** Хук для підкласів, симетричний {@link #onAddAdditionalSaveData}. */
    protected void onReadAdditionalSaveData(CompoundTag tag) {
    }

    /**
     * Форма й позиція зони — консюмер описує лише це, решта (invulnerable,
     * no-gravity, xOld-синхронізація, NBT) — дефолт бази (план 3.28,
     * {@code ZoneShapeDescriptor}: halfX/halfY/halfZ + origin).
     *
     * @param originX/Y/Z         власна абсолютна позиція центру зони.
     * @param halfX/Y/Z           анізотропні напівосі AABB (не єдиний
     *                            ізотропний halfSize) — дозволяє формі бути
     *                            широким тонким патчем, зорієнтованим по
     *                            дотичній площині батьківської структури.
     * @param parentOriginX/Y/Z   центр батьківської структури (напр. купола)
     *                            на момент спавну — статичне число для
     *                            розрахунків підкласу (напр. нормаль
     *                            рикошету), НЕ живе посилання.
     */
    public record ZoneShapeDescriptor(
            double originX, double originY, double originZ,
            float halfX, float halfY, float halfZ,
            double parentOriginX, double parentOriginY, double parentOriginZ
    ) {
        /** Спрощений фабричний метод, коли батьківське джерело не потрібне
         *  (parentOrigin = origin). */
        public static ZoneShapeDescriptor of(double x, double y, double z, float hx, float hy, float hz) {
            return new ZoneShapeDescriptor(x, y, z, hx, hy, hz, x, y, z);
        }
    }

    /**
     * Інверсія залежності до стороннього не-{@code LivingHurtEvent} джерела
     * урону (план 3.26/3.28) — той самий патерн, що {@code GunDamageBridge}.
     * Бібліотека не знає конкретного стороннього типу проєктиля/події;
     * консюмер реалізує це у своєму TACZ-специфічному (чи еквівалентному)
     * коді й підключає через {@link #bindExternalHitBridge}.
     */
    @FunctionalInterface
    public interface ExternalHitBridge {
        void onExternalHit(InvisibleZoneEntityBase zone, Entity projectile, DamageSource source, float damage);
    }
}
