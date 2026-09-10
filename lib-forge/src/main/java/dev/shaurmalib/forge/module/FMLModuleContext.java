package dev.shaurmalib.forge.module;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Контекст модуля, що передається кожному хуку при вбудовуванні.
 */
public sealed interface FMLModuleContext permits MethodContext {
    MinecraftServer server();
    IEventBus eventBus();
}

final class MethodContext implements FMLModuleContext {
    private final MinecraftServer server;
    private final IEventBus eventBus;

    MethodContext(MinecraftServer server, IEventBus eventBus) {
        this.server = server;
        this.eventBus = eventBus;
    }

    @Override
    public MinecraftServer server() {
        return server;
    }

    @Override
    public IEventBus eventBus() {
        return eventBus;
    }
}
