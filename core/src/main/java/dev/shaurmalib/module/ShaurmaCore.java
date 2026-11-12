package dev.shaurmalib.module;

import dev.shaurmalib.common.stamina.StaminaModule;
import dev.shaurmalib.common.stamina.StaminaRules;
import dev.shaurmalib.platform.Dist;
import dev.shaurmalib.platform.event.EventBus;
import dev.shaurmalib.platform.event.PlatformEvents;

/**
 * Єдина точка входу ядра для {@code loader:*}: збирає модулі й підключає їх до шини.
 * Заміна {@code ShaurmaLib.init(...).withX().build()} (2009 рядків, прив'язаних до Forge) — тепер
 * кожне {@code withX} це окремий {@link ShaurmaModule}, а цей клас — тонкий фасад.
 */
public final class ShaurmaCore {
    private ShaurmaCore() {}

    public static Builder builder(Dist dist) { return new Builder(new ModuleHost(PlatformEvents.BUS, dist)); }
    public static Builder builder(Dist dist, EventBus bus) { return new Builder(new ModuleHost(bus, dist)); }

    public static final class Builder {
        private final ModuleHost host;
        private Builder(ModuleHost host) { this.host = host; }

        public Builder with(ShaurmaModule m) { host.add(m); return this; }
        public Builder withStamina() { return withStamina(StaminaRules.defaults()); }
        public Builder withStamina(StaminaRules rules) { return with(new StaminaModule(rules)); }

        /** Підключає все зібране в порядку залежностей. Повертає хост (для {@code detachAll} і тестів). */
        public ModuleHost build() { host.attachAll(); return host; }
    }
}
