package dev.shaurmalib.common.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Декларативний опис анімованого предмета — узагальнення
 * {@code AnimatedItemSystem.ItemDefinition} з оригіналу snipers_shaurma,
 * розширене на {@link ArmOverride} (3.24a) і {@link ItemCameraTrack} (3.24b).
 * <p>
 * Це — чисті дані. Callback {@code onActivate} у оригіналі мав тип
 * {@code BiConsumer<ServerPlayer, InteractionHand>} — прямий Mojang-тип
 * {@code ServerPlayer}/{@code InteractionHand} офіційний і спільний з
 * Fabric, тому лишається тут як generic-параметризований інтерфейс
 * {@link ActivateCallback}, а не переноситься в lib-forge — увесь
 * контракт лишається у lib-common.
 * <p>
 * <b>Реєстрація в {@link ItemDefinitionRegistry} НЕ означає, що предмет
 * зобов'язаний проходити через {@code UseSessionEngine}</b> (тікова
 * activateTick/consumeTick сесія). Реальний приклад з оригіналу —
 * {@code RespawnCardItem} (SR): він тригерить свою GeckoLib-анімацію
 * "activate" напряму через {@code ItemAnimPacket} з
 * {@code SRRespawnHandler}, обходячи {@code AnimatedItemSystem.startUse}
 * повністю (сам use/consume в нього ванільний — {@code startUsingItem}/
 * {@code onStopUsing}, 300-тіковий hold). Проте {@code ItemLeftArmHideHandler}
 * оригіналу все одно ховав його реальну ліву руку (він був у жорсткому
 * {@code Set<Class<?>>} whitelist, незалежно від того, який предмет
 * взагалі використовує {@code AnimatedItemSystem}). Щоб зберегти цю
 * поведінку 1:1 без whitelist-класів: такий предмет реєструється в
 * {@link ItemDefinitionRegistry} лише заради {@link #armOverride}
 * (і, якщо треба, {@link #cameraTrack}) — {@link #activateTick}/
 * {@link #consumeTick}/{@link #onActivate} лишаються дефолтними і просто
 * ніколи не використовуються, бо мод ніколи не викликає
 * {@code ItemAnimationEngine.startUse(...)} для цього класу. І
 * {@code ItemLeftArmHideEngine}, і {@code ItemAnimPacket.ClientHandler}
 * (camera-track гілка) читають {@link #armOverrides}/{@link #cameraTrack}
 * незалежно від сесійного стану — саме так arm-hide працює для предмета,
 * що ніколи не запускає сесію.
 *
 * @param <P> тип гравця-сервера (щоб common не залежав від конкретного
 *            імпорту net.minecraft.server.level.ServerPlayer напряму тут
 *            і не змушував généric Forge-модуль тягнути зайве — на
 *            практиці lib-forge параметризує це net.minecraft.server.level.ServerPlayer).
 * @param <H> тип руки (на практиці net.minecraft.world.InteractionHand).
 */
public final class ItemDefinition<P, H> {

    public interface ActivateCallback<P, H> {
        void onActivate(P player, H hand);
    }

    public final String animationName;
    public final int activateTick;
    public final int consumeTick;
    public final ActivateCallback<P, H> onActivate;
    /**
     * Перенесено 1:1 з оригіналу — записується на кожному {@code .register(...)}
     * виклику, але ні в оригінальному {@code AnimatedItemSystem}, ні в цій
     * бібліотеці НІДЕ не читається рушієм. Кожен предмет у snipers ставить
     * власний cooldown вручну в {@code onActivate} через
     * {@code player.getCooldowns().addCooldown(...)}. Поле збережено для
     * точної відповідності оригінальному API (щоб виклики
     * {@code .useCooldown(true/false)} з мода компілювались без змін), а
     * не тому, що воно на щось впливає.
     */
    public final boolean useCooldown;
    /** Порожньо якщо жодна кістка нічого не заміняє (як зараз для більшості предметів). */
    public final Map<HandSlot, ArmOverride> armOverrides;
    public final ItemCameraTrack cameraTrack; // nullable

    private ItemDefinition(Builder<P, H> b) {
        this.animationName = b.animationName;
        this.activateTick = b.activateTick;
        this.consumeTick = b.consumeTick;
        this.onActivate = b.onActivate;
        this.useCooldown = b.useCooldown;
        this.armOverrides = Map.copyOf(b.armOverrides);
        this.cameraTrack = b.cameraTrack;
    }

    public Optional<ArmOverride> armOverride(HandSlot slot) {
        return Optional.ofNullable(armOverrides.get(slot));
    }

    public Optional<ItemCameraTrack> cameraTrack() {
        return Optional.ofNullable(cameraTrack);
    }

    public static <P, H> Builder<P, H> builder() {
        return new Builder<>();
    }

    public static final class Builder<P, H> {
        private String animationName = "use";
        private int activateTick = -1;
        private int consumeTick = 60;
        private ActivateCallback<P, H> onActivate = (p, h) -> {};
        private boolean useCooldown = true;
        private final Map<HandSlot, ArmOverride> armOverrides = new EnumMap<>(HandSlot.class);
        private ItemCameraTrack cameraTrack;

        public Builder<P, H> animationName(String v) { this.animationName = v; return this; }
        public Builder<P, H> activateTick(int v) { this.activateTick = v; return this; }
        public Builder<P, H> consumeTick(int v) { this.consumeTick = v; return this; }
        public Builder<P, H> onActivate(ActivateCallback<P, H> v) { this.onActivate = v; return this; }
        public Builder<P, H> useCooldown(boolean v) { this.useCooldown = v; return this; }

        /** Кістка {@code boneName} у моделі предмета заміняє реальну руку в слоті {@code slot}. */
        public Builder<P, H> armOverride(HandSlot slot, String boneName) {
            armOverrides.put(slot, ArmOverride.of(slot, boneName));
            return this;
        }

        public Builder<P, H> armOverride(ArmOverride override) {
            armOverrides.put(override.handSlot(), override);
            return this;
        }

        public Builder<P, H> cameraTrack(ItemCameraTrack track) {
            this.cameraTrack = track;
            return this;
        }

        public ItemDefinition<P, H> build() {
            return new ItemDefinition<>(this);
        }
    }
}