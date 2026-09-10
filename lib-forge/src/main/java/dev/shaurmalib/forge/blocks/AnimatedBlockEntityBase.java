package dev.shaurmalib.forge.blocks;

import dev.shaurmalib.common.blocks.AnimatedBlockClipSet;
import dev.shaurmalib.common.blocks.AnimatedBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Базовий клас "анімований GeckoLib-блок з меню, у яке можуть зазирати
 * кілька гравців одночасно" (план, п. 3.27) — узагальнення
 * {@code CaseBlockEntity} snipers_shaurma (532 рядки). Клас навмисно НЕ
 * розширює {@code RandomizableContainerBlockEntity} сам — контейнерна
 * частина (луут-таблиці, {@code getItems()/setItems()}, {@code createMenu})
 * лишається продуктовою специфікою консюмера (конкретний кейс,
 * конкретний розмір інвентаря за типом), бо не кожен майбутній анімований
 * блок обов'язково є контейнером. Ця база інкапсулює саме ту частину, що
 * була неявно перемішана з контейнерною логікою в оригіналі, але сама по
 * собі контейнером не є: {@link AnimatedBlockState}-lifecycle,
 * openers-лічильник, серверний тік переходів і мережева синхронізація.
 * <p>
 * Три задокументовані в оригіналі виправлення переносяться як вбудована
 * поведінка бази, а не залишаються "треба пам'ятати зробити в кожному
 * новому блоці":
 * <ol>
 *   <li><b>{@code openersCount} ніколи не скидається примусово ззовні</b>
 *       звичайним шляхом зміни стану — лише через явний
 *       {@link #resetOpenersCount()} (watchdog-виклик консюмера, наприклад
 *       при завантаженні чанку, план п. {@code onLoad()}-фікс нижче) або
 *       парний {@link #onMenuOpened()}/{@link #onMenuClosed()}. Це той
 *       самий інваріант, що вже задокументований у {@code CaseBlockEntity}
 *       коментарем "НІКОЛИ не скидається примусово ззовні".</li>
 *   <li><b>Chunk-reload watchdog для openersCount</b> — консюмер викликає
 *       {@link #resetOpenersCount()} зі свого {@code onLoad()}: якщо гравець
 *       відключився поки блок був відкритий, {@code onMenuClosed()} не
 *       викликається (клієнт зник, а не закрив екран), і лічильник
 *       назавжди лишається {@code > 0}, блокуючи {@link #triggerRefill}
 *       до перезапуску сервера. Сама база не викликає це автоматично з
 *       {@code onLoad()}, бо {@code BlockEntity.onLoad()} не є фінальним
 *       методом консюмерського підкласу — але документує обов'язковість
 *       виклику тут, а не мовчки покладається, що кожен новий блок
 *       про це згадає сам.</li>
 *   <li><b>{@code getUpdatePacket()} override — критичний, не косметичний.</b>
 *       Без нього базовий {@code BlockEntity.getUpdatePacket()} повертає
 *       {@code null}, тому {@code level.sendBlockUpdated(...)} у
 *       {@link #syncToClient()} ФАКТИЧНО НІЧОГО не надсилає жодному
 *       клієнту, що вже стежив за блоком до зміни стану (чанк вже
 *       завантажений) — такий клієнт покладався б виключно на
 *       одноразові GeckoLib trigger-пакети, які, як і задокументовано в
 *       {@link #animPredicate}, можуть не долетіти до кожного клієнта
 *       (пізня підписка на чанк). Ця база вже реалізує
 *       {@code getUpdatePacket()}/{@code getUpdateTag()}/
 *       {@code handleUpdateTag()} правильно — консюмеру НЕ треба
 *       повторювати override.</li>
 * </ol>
 * <p>
 * <b>Що описує консюмер:</b> лише {@link AnimatedBlockClipSet} (назви
 * GeckoLib-кліпів) через {@link #clipSet()}.
 * <p>
 * <b>Важливо — контейнерна частина через КОМПОЗИЦІЮ, не подвійне
 * успадкування.</b> Оригінальний {@code CaseBlockEntity} успадковував
 * {@code RandomizableContainerBlockEntity} (щоб отримати {@code Container}
 * API) і водночас містив весь lifecycle-код з цього класу. Java не
 * дозволяє консюмеру успадкувати одночасно і {@code AnimatedBlockEntityBase}
 * (яка сама успадковує {@code BlockEntity}), і
 * {@code RandomizableContainerBlockEntity} (яка теж успадковує
 * {@code BlockEntity}) — це два окремі дерева спадкування від одного
 * кореня. Якщо консюмерський блок водночас є контейнером (як кейс),
 * правильний шлях:
 * <ul>
 *   <li>консюмер розширює {@code AnimatedBlockEntityBase} напряму і сам
 *       реалізує {@link net.minecraft.world.Container} (або
 *       {@link net.minecraft.world.WorldlyContainer}) методи, тримаючи
 *       {@code NonNullList<ItemStack> items} як власне поле — той самий
 *       обсяг коду, що {@code getItems()/setItems()/getContainerSize()/
 *       getItem()/setItem()/...} у {@code CaseBlockEntity}, просто без
 *       посередника {@code RandomizableContainerBlockEntity};</li>
 *   <li>або, якщо луут-таблична підтримка {@code RandomizableContainerBlockEntity}
 *       (try-loot-table на першому відкритті) справді потрібна 1:1,
 *       консюмер лишає свій блок нащадком
 *       {@code RandomizableContainerBlockEntity} і НЕ використовує цю
 *       базу — натомість копіює собі лише потрібні шматки
 *       ({@link AnimatedBlockState}-поле, {@link #onMenuOpened}/
 *       {@link #onMenuClosed}-подібні методи, {@code getUpdatePacket()}
 *       override) як приклад, а не як спільний предок. Це єдиний
 *       модуль плану, де повне узагальнення бази конфліктує з ванільною
 *       ієрархією Minecraft, тому вибір явно лишається консюмеру, а не
 *       ховається за прихованою композицією всередині бібліотеки.</li>
 * </ul>
 */
public abstract class AnimatedBlockEntityBase extends BlockEntity implements GeoBlockEntity {

    /** Назва GeckoLib animation-контролера — фіксована, як і в оригіналі
     *  ({@code "mainCtrl"}), щоб консюмерські Blockbench-моделі, портовані
     *  зі snipers-кейсів, працювали без перейменування контролера. */
    public static final String CONTROLLER_NAME = "mainCtrl";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private AnimatedBlockState animState = AnimatedBlockState.IDLE;
    private int openersCount = 0;
    private int serverAnimTicksLeft = 0;

    private RawAnimation animIdle;
    private RawAnimation animOpen;
    private RawAnimation animClose;
    private RawAnimation animRefill;

    protected AnimatedBlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Назви GeckoLib-кліпів для цього конкретного блоку (план 3.27,
     * {@link AnimatedBlockClipSet}) — консюмер надає їх один раз, зазвичай
     * повертаючи статичну константу свого підкласу.
     */
    protected abstract AnimatedBlockClipSet clipSet();

    /**
     * Тривалість (у тіках) фази {@link AnimatedBlockState#OPENING} до
     * автоматичного переходу в {@link AnimatedBlockState#OPEN} —
     * {@code CaseBlockEntity} використовував фіксовані {@code 20} для
     * всіх типів кейсів; консюмер може перевизначити для власного блоку.
     */
    protected int openTicks() {
        return 20;
    }

    /** Тривалість фази {@link AnimatedBlockState#CLOSING}. Оригінал: {@code 25}. */
    protected int closeTicks() {
        return 25;
    }

    /** Тривалість фази {@link AnimatedBlockState#REFILLING}. Оригінал: {@code 35}. */
    protected int refillTicks() {
        return 35;
    }

    private RawAnimation idleAnim() {
        if (animIdle == null) animIdle = RawAnimation.begin().thenLoop(clipSet().idle());
        return animIdle;
    }

    private RawAnimation openAnim() {
        if (animOpen == null) animOpen = RawAnimation.begin().thenPlay(clipSet().open());
        return animOpen;
    }

    private RawAnimation closeAnim() {
        if (animClose == null) animClose = RawAnimation.begin().thenPlay(clipSet().close());
        return animClose;
    }

    private RawAnimation refillAnim() {
        if (animRefill == null) animRefill = RawAnimation.begin().thenPlay(clipSet().refill());
        return animRefill;
    }

    // ── Open / Close (план: openersCount-інваріант, п.1 вище) ──────────────

    /**
     * Викликається консюмером, коли конкретний гравець відкрив меню цього
     * блоку (типово з {@code MenuProvider}/{@code openMenu}-шляху). Перший
     * гравець, що відкрив — запускає перехід {@code IDLE → OPENING}; кожен
     * наступний лише інкрементує лічильник без повторного тригера анімації.
     */
    public final void onMenuOpened() {
        openersCount++;
        if (openersCount == 1 && level != null && !level.isClientSide) {
            setAnimStateServer(AnimatedBlockState.OPENING);
            triggerAnim(CONTROLLER_NAME, "open");
            this.serverAnimTicksLeft = openTicks();
        }
    }

    /**
     * Симетрично {@link #onMenuOpened()} — консюмер викликає при закритті
     * екрана конкретним гравцем. Перехід у {@code CLOSING} відбувається
     * лише коли лічильник дійшов до нуля І блок не в фазі
     * {@link AnimatedBlockState#REFILLING} (той самий пріоритет, що в
     * оригіналі — рефіл не переривається закриттям чийогось меню).
     */
    public final void onMenuClosed() {
        openersCount = Math.max(0, openersCount - 1);
        if (openersCount == 0 && animState != AnimatedBlockState.REFILLING
                && level != null && !level.isClientSide) {
            setAnimStateServer(AnimatedBlockState.CLOSING);
            triggerAnim(CONTROLLER_NAME, "close");
            this.serverAnimTicksLeft = closeTicks();
        }
    }

    /**
     * Запускає перехід у {@link AnimatedBlockState#REFILLING}. НЕ скидає
     * {@link #openersCount} примусово (план-інваріант вище) — якщо хтось
     * саме зараз дивиться в меню, анімація рефілу все одно запуститься,
     * але лічильник лишиться коректним для подальших
     * {@link #onMenuOpened()}/{@link #onMenuClosed()} викликів. Консюмер
     * відповідає за фактичну заміну вмісту контейнера окремо (ця база не
     * знає про інвентар); типовий порядок виклику: спершу очистити/
     * перегенерувати лут, потім викликати {@link #triggerRefill()}.
     */
    public final void triggerRefill() {
        setAnimStateServer(AnimatedBlockState.REFILLING);
        if (level != null && !level.isClientSide) {
            triggerAnim(CONTROLLER_NAME, "refill");
        }
        this.serverAnimTicksLeft = refillTicks();
    }

    public final AnimatedBlockState animState() {
        return animState;
    }

    /** Кількість гравців, що зараз мають меню цього блоку відкритим. */
    public final int getOpenersCount() {
        return openersCount;
    }

    /**
     * Примусовий watchdog-скид лічильника — консюмер викликає це зі свого
     * {@code onLoad()} (план, п.2 вище: гравець відключився з відкритим
     * меню, парний {@link #onMenuClosed()} ніколи не прийде). НЕ
     * викликати з ігрового коду при звичайному закритті меню —
     * для цього є {@link #onMenuClosed()}, який коректно декрементує, а
     * не обнуляє.
     */
    public final void resetOpenersCount() {
        this.openersCount = 0;
        if (this.animState != AnimatedBlockState.IDLE) {
            this.animState = AnimatedBlockState.IDLE;
            this.setChanged();
        }
    }

    private void setAnimStateServer(AnimatedBlockState newState) {
        this.animState = newState;
        this.setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── Серверний тік (перехід між фазами й авто-закриття) ──────────────────

    /**
     * Викликається з {@code BlockEntityTicker} консюмера
     * (реєструється у {@code getTicker(...)} блоку, як зараз
     * {@code CaseBlockEntity.serverTick}). Веде відлік
     * {@link #serverAnimTicksLeft} і переводить {@code OPENING → OPEN},
     * {@code CLOSING → IDLE}, {@code REFILLING → IDLE}; додатково
     * автоматично починає закриття, якщо блок лишився {@code OPEN} з
     * нульовим {@link #openersCount} (наприклад останній гравець вийшов
     * без штатного {@link #onMenuClosed()} — той самий сценарій, що й
     * chunk-reload watchdog, але в межах одного тіку без релоаду чанку).
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AnimatedBlockEntityBase be) {
        if (level.isClientSide) return;

        if (be.serverAnimTicksLeft > 0) {
            be.serverAnimTicksLeft--;
            if (be.serverAnimTicksLeft == 0) {
                switch (be.animState) {
                    case OPENING -> be.setAnimStateServer(AnimatedBlockState.OPEN);
                    case CLOSING -> be.setAnimStateServer(AnimatedBlockState.IDLE);
                    case REFILLING -> be.setAnimStateServer(AnimatedBlockState.IDLE);
                    default -> { }
                }
            }
        }

        if (be.animState == AnimatedBlockState.OPEN && be.openersCount == 0) {
            be.setAnimStateServer(AnimatedBlockState.CLOSING);
            be.triggerAnim(CONTROLLER_NAME, "close");
            be.serverAnimTicksLeft = be.closeTicks();
        }
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────

    @Override
    public final void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER_NAME, 5, this::animPredicate)
                .triggerableAnim("open", openAnim())
                .triggerableAnim("close", closeAnim())
                .triggerableAnim("refill", refillAnim()));
    }

    /**
     * ВИПРАВЛЕННЯ, перенесене з {@code CaseBlockEntity} 1:1: для кожного
     * проміжного стану явно задаємо відповідну {@code RawAnimation} через
     * {@link #animState} напряму, а не покладаємось лише на мережевий
     * {@code triggerAnim(...)}. {@code triggerAnim} — одноразова подія;
     * клієнт, що підписався на цю {@code BlockEntity} ПІЗНІШЕ, ніж сервер
     * надіслав тригер (гравець підійшов уже після того, як інший гравець
     * закрив блок і пішов), ніколи не отримає той пакет, хоча
     * {@code animState} він і так отримує окремим block-update
     * синком. Явний switch тут гарантує, що будь-який клієнт, незалежно
     * від моменту підписки, бачить анімацію, що відповідає ПОТОЧНОМУ
     * логічному стану, а не історії тригерів.
     */
    private PlayState animPredicate(AnimationState<AnimatedBlockEntityBase> state) {
        return switch (animState) {
            case IDLE -> state.setAndContinue(idleAnim());
            case OPEN -> state.setAndContinue(openAnim());
            case CLOSING -> state.setAndContinue(closeAnim());
            case REFILLING -> state.setAndContinue(refillAnim());
            // OPENING: тригер "open" вже надійшов через triggerAnim() при
            // переході в цей стан — навмисно не перезапускаємо ANIM_OPEN
            // тут, щоб не "зациклити" анімацію відкриття з нуля щокадру
            // для клієнта, що вже й так її грає. Для пізніх клієнтів
            // невеликий пропуск початку прийнятний — фаза коротка.
            default -> PlayState.CONTINUE;
        };
    }

    @Override
    public final AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ── Мережева синхронізація (план: getUpdatePacket-фікс, п.3 вище) ──────

    @Override
    public final CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public final Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public final void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    // ── NBT ───────────────────────────────────────────────────────────────

    /** Ключ NBT для {@link #animState} — консюмер може продовжити
     *  використовувати власний префікс полів поряд із цим. */
    private static final String NBT_ANIM_STATE = "shaurma_anim_state";

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(NBT_ANIM_STATE)) {
            try {
                this.animState = AnimatedBlockState.valueOf(tag.getString(NBT_ANIM_STATE));
            } catch (IllegalArgumentException e) {
                this.animState = AnimatedBlockState.IDLE;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_ANIM_STATE, this.animState.name());
    }
}
