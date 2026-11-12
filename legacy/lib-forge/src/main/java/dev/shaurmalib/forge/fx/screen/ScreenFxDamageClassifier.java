package dev.shaurmalib.forge.fx.screen;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;

/**
 * Серверна класифікація ванільного урону для екранних ефектів (порт з EnhancedVisuals через snipers_shaurma).
 * Модам зі своєю системою HP (маніяк) класифікатор не потрібен — вони шлють пресет напряму
 * ({@link ScreenFxServer#play}).
 */
public final class ScreenFxDamageClassifier {
    private ScreenFxDamageClassifier() {}

    public static ScreenFxDamageKind classify(DamageSource source) {
        if (source.getDirectEntity() instanceof AbstractArrow) return ScreenFxDamageKind.PIERCE;
        if (source.getDirectEntity() instanceof LivingEntity l) {
            ItemStack weapon = l.getMainHandItem();
            if (!weapon.isEmpty()) {
                if (weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem) return ScreenFxDamageKind.SLASH;
                if (weapon.getItem() instanceof PickaxeItem || weapon.getItem() instanceof ShovelItem) return ScreenFxDamageKind.IMPACT;
                if (weapon.getItem() instanceof HoeItem) return ScreenFxDamageKind.PIERCE;
                return ScreenFxDamageKind.SPLATTER;
            }
            if (l instanceof Zombie || l instanceof Skeleton || l instanceof Ocelot) return ScreenFxDamageKind.SLASH;
            if (l instanceof AbstractGolem || l instanceof Player) return ScreenFxDamageKind.IMPACT;
            if (l instanceof Wolf || l instanceof Spider) return ScreenFxDamageKind.PIERCE;
            return ScreenFxDamageKind.SPLATTER;
        }
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) return ScreenFxDamageKind.PIERCE;
        if (source.is(DamageTypeTags.IS_FALL)) return ScreenFxDamageKind.IMPACT;
        if (source.is(DamageTypeTags.IS_FREEZING)) return ScreenFxDamageKind.FREEZE;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.ON_FIRE)) return ScreenFxDamageKind.FIRE;
        if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_LIGHTNING)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.STARVE)) {
            return ScreenFxDamageKind.NONE;
        }
        return ScreenFxDamageKind.SPLATTER;
    }
}
