package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.common.mixin.MixinId;
import dev.shaurmalib.common.mixin.MixinToggleRegistry;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * {@link IMixinConfigPlugin} бібліотеки (план, п. 3.17) — точка, де
 * Mixin-транформер питає "чи застосовувати цей миксин" ДО того, як
 * FML переходить до основного lifecycle мода. Підключається через
 * {@code "plugin"}-ключ у {@code shaurma_lib.mixins.json}.
 * <p>
 * Логіка навмисно мінімальна: миксин, чиє ім'я класу (без пакета)
 * відповідає одному з {@link MixinId#defaultClassName()}, застосовується
 * лише якщо {@link MixinToggleRegistry#isEnabled(MixinId)} повертає
 * {@code true}. Миксини, що НЕ відповідають жодному {@link MixinId}
 * (наприклад {@code MixinChatComponent}, {@code MixinItemInHandRenderer}
 * — завжди активні, самі перевіряють прапорець свого модуля зсередини),
 * пропускаються без перевірки — вони застосовуються завжди, а
 * internal-guard (чи підключено {@code withChat()}/{@code
 * withAnimatedItems()} тощо) вирішує, чи виконувати ефективний код
 * усередині {@code @Inject}-методу.
 */
public final class ShaurmaLibMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Порядок гарантовано ДО завантаження цільових класів (FML запускає
        // конструктори @Mod-класів, де консюмер викликає withMixins(...),
        // раніше, ніж торкається рендер/модель класів гри) — жодної
        // додаткової синхронізації тут не потрібно.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = simpleNameOf(mixinClassName);
        MixinId id = MixinToggleRegistry.byClassName(simpleName);
        if (id == null) {
            // Не toggle-миксин бібліотеки (наприклад MixinChatComponent) — завжди застосовується.
            return true;
        }
        return MixinToggleRegistry.isEnabled(id);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Не потрібно — жодних крос-плагінних узгоджень цілей.
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Не потрібно.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Не потрібно.
    }

    private static String simpleNameOf(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }
}
