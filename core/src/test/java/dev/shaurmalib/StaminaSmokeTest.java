package dev.shaurmalib;

import static dev.shaurmalib.CoreSmokeTest.check;

import dev.shaurmalib.common.net.ShaurmaPackets;
import dev.shaurmalib.common.net.StaminaSyncPacket;
import dev.shaurmalib.common.stamina.*;
import dev.shaurmalib.module.*;
import dev.shaurmalib.platform.Dist;
import dev.shaurmalib.platform.Platform;
import dev.shaurmalib.platform.event.EventBus;
import dev.shaurmalib.platform.event.PlatformEvents;
import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.net.PacketContext;
import dev.shaurmalib.platform.ref.*;
import dev.shaurmalib.platform.service.*;
import dev.shaurmalib.render.cmd.RecordingCanvas;
import dev.shaurmalib.render.cmd.RenderCommand;
import dev.shaurmalib.render.overlay.OverlayRegistry;
import java.util.*;

/** Stamina: чиста state-machine, сервіс на фейках, модуль через шину, HUD через RecordingCanvas. Жодного Minecraft. */
final class StaminaSmokeTest {
    static final class P implements PlayerRef {
        final UUID id = UUID.randomUUID();
        boolean creative, sprinting, riding; int tick; int food = 5; float sat = 0;
        public UUID uuid() { return id; } public Vec3d position() { return new Vec3d(0, 0, 0); }
        public LevelRef level() { return null; } public String name() { return "p"; } public Object raw() { return this; }
    }
    static final class Ops implements PlayerOps {
        public boolean isCreativeOrSpectator(PlayerRef p) { return ((P) p).creative; }
        public boolean isSprinting(PlayerRef p) { return ((P) p).sprinting; }
        public void setSprinting(PlayerRef p, boolean s) { ((P) p).sprinting = s; }
        public boolean isRiding(PlayerRef p) { return ((P) p).riding; }
        public int tickCount(PlayerRef p) { return ((P) p).tick; }
        public void setFood(PlayerRef p, int f, float s) { ((P) p).food = f; ((P) p).sat = s; }
    }
    static final class Store implements PlayerPersistence {
        final Map<String, Double> m = new HashMap<>();
        public OptionalDouble getNumber(PlayerRef p, String k) { Double d = m.get(p.uuid() + k); return d == null ? OptionalDouble.empty() : OptionalDouble.of(d); }
        public void putNumber(PlayerRef p, String k, double v) { m.put(p.uuid() + k, v); }
        public void remove(PlayerRef p, String k) { m.remove(p.uuid() + k); }
    }
    static final class Net implements NetworkSender {
        final List<Object> sent = new ArrayList<>();
        public void sendToPlayer(PlayerRef p, Object pkt) { sent.add(pkt); }
        public void sendToAllPlayers(Object pkt) { sent.add(pkt); }
        public void sendToServer(Object pkt) { sent.add(pkt); }
    }

    static void run() {
        // ---- 1. Engine ----
        StaminaRules r = StaminaRules.builder().maxStamina(100).drainPerSecond(20).recoveryPerSecond(20)
                .emptyRecoveryDelaySeconds(1).recoveryDelaySeconds(0.5f).sprintResumeFraction(0.2f).build();
        StaminaState s = new StaminaState(100);
        for (int t = 0; t < 20; t++) StaminaEngine.step(r, s, new StaminaEngine.Input(t, true, false));
        check(Math.abs(s.value() - 80f) < 1e-3, "engine: 1s sprint drains 20");
        for (int t = 20; t < 100 && !s.exhausted(); t++) StaminaEngine.step(r, s, new StaminaEngine.Input(t, true, false));
        check(s.exhausted() && s.value() == 0, "engine: depletes to 0 and becomes exhausted");
        StaminaEngine.Result stop = StaminaEngine.step(r, s, new StaminaEngine.Input(101, true, false));
        check(stop.stopSprinting(), "engine: exhausted sprinter must be stopped");
        int base = s.lastUseTick();
        StaminaEngine.step(r, s, new StaminaEngine.Input(base + 10, false, false));
        check(s.value() == 0, "engine: no recovery before empty-delay (1s)");
        for (int t = base + 20; t < base + 20 + 12; t++) StaminaEngine.step(r, s, new StaminaEngine.Input(t, false, false));
        check(s.value() > 0 && s.exhausted(), "engine: recovering but still exhausted below 20%");
        for (int t = base + 32; t < base + 32 + 40; t++) StaminaEngine.step(r, s, new StaminaEngine.Input(t, false, false));
        check(!s.exhausted(), "engine: exhaustion cleared at >= 20% of max");
        StaminaState riding = new StaminaState(100);
        StaminaEngine.step(r, riding, new StaminaEngine.Input(1, true, true));
        check(riding.value() == 100, "engine: riding sprinter does not drain");

        // ---- 2. Service on fakes ----
        Ops ops = new Ops(); Store store = new Store(); Net net = new Net();
        StaminaService svc = new StaminaService(ops, store, net);
        P p = new P();
        try { svc.setStamina(p, 1); check(false, "service requires enable"); }
        catch (IllegalStateException e) { check(true, "service requires enable"); }
        svc.enable(r);
        svc.onLogin(p);
        check(net.sent.size() == 1 && ((StaminaSyncPacket) net.sent.get(0)).active(), "service: login sync sent");
        p.sprinting = true; p.tick = 1; svc.tick(p);
        check(p.food == 20 && p.sat == 5.0f, "service: hunger forced full");
        check(store.m.containsKey(p.uuid() + StaminaService.PERSIST_KEY), "service: value persisted");
        net.sent.clear();
        p.sprinting = false; p.tick = 2; svc.tick(p);
        check(net.sent.isEmpty(), "service: unchanged state is not re-sent (dedupe)");
        svc.setStamina(p, 0);
        check(svc.shouldBlockSprint(p) && !svc.shouldBlockJump(p), "service: blocks sprint at 0, jump only if rule on");
        p.creative = true;
        check(!svc.shouldBlockSprint(p), "service: creative is exempt");
        p.creative = false;
        P q = new P(); store.putNumber(q, StaminaService.PERSIST_KEY, 33.0);
        check(svc.getStamina(q) == 33f, "service: restores persisted stamina on first access");
        svc.clear(p);
        check(net.sent.get(net.sent.size() - 1).equals(StaminaSyncPacket.disabled()), "service: clear sends disabled");

        // ---- 3. Module over the event bus (client+server) ----
        Platform.reset();
        Ops ops2 = new Ops(); Store st2 = new Store(); Net net2 = new Net();
        P local = new P();
        Platform.register(PlayerOps.class, ops2);
        Platform.register(PlayerPersistence.class, st2);
        Platform.register(NetworkSender.class, net2);
        Platform.register(ClientAccess.class, () -> Optional.of(local));
        ShaurmaPackets.ensureLoaded();
        EventBus bus = new EventBus();
        StaminaModule mod = new StaminaModule(r);
        ModuleHost host = ShaurmaCore.builder(Dist.CLIENT, bus).with(mod).build();
        bus.post(new PlatformEvents.PlayerJoin(local));
        check(net2.sent.size() == 1, "module: PlayerJoin -> sync via bus");
        // сервер відповів пакетом -> клієнт застосував
        Runnable[] queued = new Runnable[1];
        PacketContext pc = new PacketContext() {
            public Optional<PlayerRef> sender() { return Optional.empty(); }
            public void enqueue(Runnable x) { queued[0] = x; }
        };
        ShaurmaPackets.STAMINA_SYNC.dispatch(new StaminaSyncPacket(true, 0, 100, true, true), pc);
        queued[0].run();
        check(mod.clientState().shouldBlockSprint() && mod.clientState().shouldBlockJump(), "module: S2C packet updates client state");
        check(StaminaGates.blockSprint(local, true) && StaminaGates.blockJump(local, true), "gates: client blocks local player");
        check(!StaminaGates.blockSprint(new P(), true), "gates: client ignores other players");
        local.sprinting = true;
        bus.post(new PlatformEvents.ClientPlayerTick(local));
        check(!local.sprinting, "module: client tick clears blocked sprint");
        // HUD через RecordingCanvas
        mod.clientState().apply(new StaminaSyncPacket(true, 50, 100, false, false));
        RecordingCanvas cv = new RecordingCanvas(640, 360);
        OverlayRegistry.renderAll(cv, 0f);
        long fills = cv.commands().stream().filter(c -> c instanceof RenderCommand.Fill).count();
        check(fills == 2, "hud: bar = background + fill (" + fills + ")");
        mod.clientState().clear();
        RecordingCanvas cv2 = new RecordingCanvas(640, 360);
        OverlayRegistry.renderAll(cv2, 0f);
        check(cv2.commands().isEmpty(), "hud: nothing drawn when inactive");
        host.detachAll();
        int before = net2.sent.size();
        bus.post(new PlatformEvents.PlayerJoin(local));
        check(net2.sent.size() == before, "module: detach unsubscribes");

        // ---- 4. ModuleHost ordering / errors ----
        List<String> log = new ArrayList<>();
        class M implements ShaurmaModule {
            final String id; final Set<String> deps;
            M(String id, String... d) { this.id = id; this.deps = Set.of(d); }
            public String id() { return id; } public Set<String> dependsOn() { return deps; }
            public void attach(ModuleContext c) { log.add(id); }
        }
        new ModuleHost(new EventBus(), Dist.DEDICATED_SERVER).add(new M("c", "b")).add(new M("b", "a")).add(new M("a")).attachAll();
        check(log.equals(List.of("a", "b", "c")), "host: topological attach order " + log);
        try { new ModuleHost(new EventBus(), Dist.CLIENT).add(new M("x", "y")).add(new M("y", "x")).attachAll(); check(false, "host: cycle detected"); }
        catch (IllegalStateException e) { check(e.getMessage().contains("Цикл"), "host: cycle detected"); }
        try { new ModuleHost(new EventBus(), Dist.CLIENT).add(new M("x", "missing")).attachAll(); check(false, "host: missing dep"); }
        catch (IllegalStateException e) { check(e.getMessage().contains("missing"), "host: missing dep reported"); }
        Platform.reset();
    }
}
