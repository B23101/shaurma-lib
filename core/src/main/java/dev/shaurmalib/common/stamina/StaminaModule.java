package dev.shaurmalib.common.stamina;

import dev.shaurmalib.common.net.ShaurmaPackets;
import dev.shaurmalib.module.ModuleContext;
import dev.shaurmalib.module.ShaurmaModule;
import dev.shaurmalib.platform.Platform;
import dev.shaurmalib.platform.event.EventBus;
import dev.shaurmalib.platform.event.PlatformEvents;
import dev.shaurmalib.platform.service.ClientAccess;
import dev.shaurmalib.platform.service.NetworkSender;
import dev.shaurmalib.platform.service.PlayerOps;
import dev.shaurmalib.platform.service.PlayerPersistence;
import dev.shaurmalib.render.overlay.OverlayRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Підключає stamina: сервісом на сервері, дзеркалом+HUD на клієнті. Замінює
 * {@code StaminaHooks}/{@code StaminaClientHooks} (Forge {@code @Mod.EventBusSubscriber}) та гілку Builder.withStamina.
 * Єдине джерело правди про «що саме вішати» — тут, для всіх лоадерів і версій.
 */
public final class StaminaModule implements ShaurmaModule {
    public static final String ID = "stamina";

    private final StaminaRules defaults;
    private final List<EventBus.Subscription> subs = new ArrayList<>();
    private StaminaService service;
    private StaminaClientState clientState;
    private StaminaHud hud;

    public StaminaModule(StaminaRules defaults) { this.defaults = Objects.requireNonNull(defaults); }

    @Override public String id() { return ID; }

    public StaminaService service() { return service; }
    public StaminaClientState clientState() { return clientState; }
    public StaminaHud hud() { return hud; }

    @Override
    public void attach(ModuleContext ctx) {
        PlayerOps ops = Platform.service(PlayerOps.class);
        service = new StaminaService(ops, Platform.service(PlayerPersistence.class), Platform.service(NetworkSender.class));
        service.enable(defaults);
        clientState = new StaminaClientState();
        hud = new StaminaHud(clientState);

        EventBus bus = ctx.bus();
        subs.add(bus.subscribe(PlatformEvents.ServerPlayerTick.class, e -> service.tick(e.player())));
        subs.add(bus.subscribe(PlatformEvents.PlayerJoin.class, e -> service.onLogin(e.player())));
        subs.add(bus.subscribe(PlatformEvents.PlayerLeave.class, e -> service.onLogout(e.player())));

        ClientAccess client = ctx.isClient() ? Platform.service(ClientAccess.class) : null;
        StaminaGates.bind(service, clientState,
                p -> client != null && client.localPlayer().map(l -> l.uuid().equals(p.uuid())).orElse(false));

        if (ctx.isClient()) {
            OverlayRegistry.register(StaminaHud.ID, hud);
            ShaurmaPackets.STAMINA_SYNC.bind((pkt, pc) -> pc.enqueue(() -> clientState.apply(pkt)));
            subs.add(bus.subscribe(PlatformEvents.ClientDisconnect.class, e -> clientState.clear()));
            // Міксин не дає ВВІМКНУТИ спринт, але вже ввімкнений сам не згасне → гасимо на початку тіку.
            subs.add(bus.subscribe(PlatformEvents.ClientPlayerTick.class, e -> {
                if (ops.isSprinting(e.player()) && clientState.shouldBlockSprint()) ops.setSprinting(e.player(), false);
            }));
        }
    }

    @Override
    public void detach() {
        subs.forEach(EventBus.Subscription::unsubscribe);
        subs.clear();
        StaminaGates.unbind();
        OverlayRegistry.unregister(StaminaHud.ID);
    }
}
