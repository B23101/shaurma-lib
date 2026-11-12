package dev.shaurmalib.forge.mode;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * Перенесено 1:1 з {@code org.example.snipers_shaurma.core.api.ModeCommand}.
 * Дозволяє режиму додати свої підкоманди до кореневої команди бібліотеки
 * (модуль 3.30, {@code ShaurmaCommandRoot}) — той самий Brigadier-патерн,
 * що вже використовується в {@code SGCommand} для {@code /sg}.
 */
public interface ModeCommandContribution {
    void registerSubcommands(LiteralArgumentBuilder<CommandSourceStack> commandRoot);
}
