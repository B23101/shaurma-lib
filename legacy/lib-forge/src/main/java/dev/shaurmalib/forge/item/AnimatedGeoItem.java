package dev.shaurmalib.forge.item;

import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Контракт для анімованих предметів бібліотеки (план, п. 3.6) —
 * узагальнення абстрактного класу {@code AnimatedGeoItem} з
 * {@code core.items.base} snipers_shaurma.
 * <p>
 * Оригінал — {@code abstract class AnimatedGeoItem extends Item implements
 * GeoItem}: конкретний базовий клас з одним контролером {@code "mainCtrl"}
 * (idle loop за замовчуванням, підклас реєструє власні triggerable
 * анімації поверх нього). Тут той самий контракт винесено як
 * {@code interface} з default-методами замість {@code abstract class},
 * щоб не займати єдине місце успадкування в ієрархії {@code Item}
 * консюмера — Java не дозволяє множинне успадкування класів, а різні
 * предмети мода-споживача цілком можуть мати власну потребу успадкування
 * (наприклад від іншого спільного базового {@code Item} свого мода).
 * Предмет реалізує інтерфейс і викликає {@link #registerAnimatedControllers}
 * зі свого {@code registerControllers(...)}.
 * <p>
 * {@link #MAIN_CONTROLLER} і {@link #IDLE_ANIM_NAME} — рядкові константи
 * імені контролера/анімації, на які напряму посилаються
 * {@link ItemLeftArmHideEngine} і мережевий
 * {@link dev.shaurmalib.forge.network.packets.ItemAnimPacket} (сервер
 * тригерить анімацію по імені контролера, клієнт перевіряє чи поточна
 * анімація — не idle, щоб вирішити, чи ховати руку) — імена мають
 * лишатись саме такими рядками, як і в оригіналі, бо самі .geo.json/
 * анімаційні файли snipers_shaurma вже використовують контролер з іменем
 * {@code "mainCtrl"} і базову анімацію з іменем {@code "idle"}.
 */
public interface AnimatedGeoItem extends GeoItem {

    /** Ім'я єдиного анімаційного контролера предмета — фіксоване, як в оригіналі. */
    String MAIN_CONTROLLER = "mainCtrl";

    /** Ім'я базової loop-анімації, до якої контролер повертається після будь-якого triggerable-кліпу. */
    String IDLE_ANIM_NAME = "idle";

    RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop(IDLE_ANIM_NAME);

    /**
     * Викликається клієнтським рендерером ({@code renderByItem}) перед
     * кожним рендером, щоб предмет знав поточний {@link ItemDisplayContext}
     * (перша особа/третя особа/GUI/...) — потрібно
     * {@link AnimatedGeoItemRenderer#renderArmSkinOverBone} для рішення
     * "чи зараз перша особа" (заміна руки скіном гравця має сенс лише
     * в першій особі).
     */
    void getTransformType(ItemDisplayContext type);

    /**
     * Підклас реєструє власні triggerable-анімації поверх дефолтного
     * idle-контролера — прямий аналог оригінального
     * {@code registerTriggerables(AnimationController<AnimatedGeoItem> ctrl)}.
     * Викликається зсередини {@link #registerAnimatedControllers}.
     */
    default void registerTriggerables(AnimationController<?> ctrl) {}

    /**
     * Стандартна реалізація {@code GeoAnimatable.registerControllers(...)} —
     * предмет викликає це зі свого перевизначення один раз:
     * <pre>{@code
     * @Override
     * public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
     *     AnimatedGeoItem.registerAnimatedControllers(this, controllers);
     * }
     * }</pre>
     * Створює контролер {@link #MAIN_CONTROLLER} з ідентичним оригіналу
     * predicate (idle грає лише коли контролер {@code STOPPED} —
     * тобто попередній triggerable-кліп завершився; інакше поточна
     * анімація просто продовжується), тригерить {@link #registerTriggerables}
     * для предмет-специфічних кліпів.
     */
    @SuppressWarnings("unchecked")
    static <T extends AnimatedGeoItem> void registerAnimatedControllers(
            T item, AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<T> ctrl = new AnimationController<>(
                item, MAIN_CONTROLLER, 0, state -> animPredicate((AnimationState<T>) state));
        ctrl.triggerableAnim(IDLE_ANIM_NAME, ANIM_IDLE);
        item.registerTriggerables((AnimationController<?>) ctrl);
        controllers.add(ctrl);
    }

    private static <T extends AnimatedGeoItem> PlayState animPredicate(AnimationState<T> state) {
        if (state.getController().getAnimationState() == AnimationController.State.STOPPED) {
            return state.setAndContinue(ANIM_IDLE);
        }
        return PlayState.CONTINUE;
    }

    /**
     * Зручний {@link AnimatableInstanceCache} для предметів (Item —
     * синглтон у Minecraft, тому потрібен {@link SingletonAnimatableInstanceCache},
     * не звичайний instance-based кеш) — предмет створює одне поле
     * {@code private final AnimatableInstanceCache cache = AnimatedGeoItem.newCache(this);}
     * і повертає його з {@code getAnimatableInstanceCache()}.
     */
    static AnimatableInstanceCache newCache(AnimatedGeoItem item) {
        return new SingletonAnimatableInstanceCache(item);
    }
}
