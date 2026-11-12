package dev.shaurmalib;

import dev.shaurmalib.common.chat.ChatFormatEngine;
import dev.shaurmalib.common.damage.DamageContext;
import dev.shaurmalib.common.damage.DamageInterceptorRegistry;
import dev.shaurmalib.common.offline.OfflineSpec;
import dev.shaurmalib.common.teleport.TeleportService;
import dev.shaurmalib.platform.*;
import dev.shaurmalib.platform.data.DataTag;
import dev.shaurmalib.platform.math.Vec3d;
import dev.shaurmalib.platform.ref.*;
import dev.shaurmalib.platform.service.*;
import dev.shaurmalib.platform.text.Text;
import dev.shaurmalib.render.cmd.RecordingCanvas;
import dev.shaurmalib.render.cmd.RenderCommand;
import dev.shaurmalib.render.overlay.CountdownOverlay;
import java.nio.file.Path;
import dev.shaurmalib.common.damage.DamageGuard;
import dev.shaurmalib.common.net.*;
import dev.shaurmalib.platform.event.*;
import dev.shaurmalib.platform.net.*;
import java.util.*;

/** Тести без фреймворку: запускаються `java`-ом, жодного Minecraft на classpath. */
public final class CoreSmokeTest {
    static int failed;
    static void check(boolean ok, String name) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failed++;
    }

    // ---- фейкові "ігрові" об'єкти — доказ, що ядро працює без гри ----
    record FakeLevel(ResourceId dimension) implements LevelRef { public Object raw() { return this; } }
    static final class FakePlayer implements PlayerRef {
        final UUID id = UUID.randomUUID(); LevelRef level; Vec3d pos = new Vec3d(1, 2, 3);
        FakePlayer(LevelRef l) { level = l; }
        public UUID uuid() { return id; } public Vec3d position() { return pos; }
        public LevelRef level() { return level; } public String name() { return "Steve"; }
        public Object raw() { return this; }
    }

    public static void main(String[] a) {
        // 1. Text / DataTag
        Text t = ChatFormatEngine.formatChatLine("Steve", "hi", "#FF0000");
        check(t.plain().equals("Steve: hi"), "ChatFormatEngine -> Text.plain");
        check(t.siblings().get(0).style().rgb() == 0xFF0000, "name colour from team hex");
        DataTag d = new DataTag().putInt("hp", 20).putTag("in", new DataTag().putString("k", "v"));
        DataTag c = d.copy(); d.putInt("hp", 1);
        check(c.getInt("hp").orElse(-1) == 20 && !c.equals(d), "DataTag deep copy");
        check(OfflineSpec.none().extra().isEmpty(), "OfflineSpec.extra is DataTag");

        // 2. Teleport orchestration with a fake backend
        List<String> calls = new ArrayList<>();
        TeleportBackend be = new TeleportBackend() {
            public void teleport(PlayerRef p, LevelRef l, double x, double y, double z, float yw, float pt) { calls.add("tp"); }
            public void ensureChunkLoaded(LevelRef l, double x, double z) { calls.add("chunk"); }
            public void resetMotionAndFall(PlayerRef p) { calls.add("reset"); }
            public boolean hasPassengers(PlayerRef p) { return false; }
            public boolean isPassenger(PlayerRef p) { calls.add("isPassenger"); return true; }
            public void ejectPassengers(PlayerRef p) { calls.add("eject"); }
        };
        PlatformInfo info = new PlatformInfo() {
            public Loader loader() { return Loader.FABRIC; } public String minecraftVersion() { return "26.3"; }
            public Dist dist() { return Dist.DEDICATED_SERVER; } public Path configDir() { return Path.of("."); }
            public boolean isModLoaded(String id) { return false; }
        };
        TextBridge tb = new TextBridge() {
            public Object toNative(Text x) { return x.plain(); }
            public Text fromNative(Object o) { return Text.literal(String.valueOf(o)); }
            public String toJson(Text x) { return x.plain(); }
            public Text fromJson(String j) { return Text.literal(j); }
        };
        Platform.install(info, be, (lv, p, items) -> {}, tb);
        check(Platform.info().isUnobfuscatedEra(), "26.3 detected as unobfuscated era");
        LevelRef overworld = new FakeLevel(ResourceId.parse("overworld"));
        List<TeleportService.TeleportedEvent> events = new ArrayList<>();
        TeleportService.addListener(events::add);
        TeleportService.teleport(new FakePlayer(overworld), overworld, 10, 64, 10, 0, 0, TeleportService.TeleportReason.RESPAWN);
        check(calls.equals(List.of("chunk", "reset", "isPassenger", "eject", "tp")), "teleport step order: " + calls);
        check(events.size() == 1 && events.get(0).toX() == 10 && events.get(0).fromX() == 1, "TeleportedEvent dispatched");
        calls.clear();
        TeleportService.teleport(new FakePlayer(overworld), new FakeLevel(ResourceId.parse("the_nether")), 0, 64, 0, 0, 0);
        check(!calls.contains("chunk"), "no chunk preload across dimensions");

        // 3. Damage registry on handles
        DamageInterceptorRegistry.register("t", (v, s, amt, ctx) -> amt > 5);
        FakePlayer p = new FakePlayer(overworld);
        DamageContext ctx = new DamageContext(DamageContext.SourceKind.VANILLA, "fall", 6f, false);
        check(DamageInterceptorRegistry.isBlocked(p, null, 6f, ctx), "interceptor blocks");
        check(!DamageInterceptorRegistry.isBlocked(p, null, 1f, ctx), "interceptor passes");

        // 4. Rendering logic with NO Minecraft
        long[] now = {1000};
        CountdownOverlay cd = new CountdownOverlay(() -> now[0]);
        RecordingCanvas cv = new RecordingCanvas(640, 360);
        cd.render(cv, 0f);
        check(cv.commands().isEmpty(), "inactive overlay draws nothing");
        cd.show(3, 0xFFFFFF00);
        now[0] = 1000 + 400; // hold phase
        cd.render(cv, 0f);
        List<RenderCommand> cmds = cv.commands();
        check(cmds.size() == 5 && cmds.get(1) instanceof RenderCommand.Translate tr && tr.x() == 320f && tr.y() == 152f, "countdown centred (320,152)");
        check(cmds.get(2) instanceof RenderCommand.Scale s && s.sx() == 4f, "hold-phase scale 1.0*4");
        check(cmds.get(3) instanceof RenderCommand.DrawText dt && dt.text().plain().equals("3") && dt.argb() == 0xFFFFFF00, "digit drawn full alpha");
        // replay на іншому канвасі дає ідентичний кадр
        RecordingCanvas other = new RecordingCanvas(640, 360);
        cv.replay(other);
        check(other.commands().equals(cmds), "replay reproduces frame");
        now[0] = 1000 + 220 + 620 + 160 + 1; // expired
        RecordingCanvas cv2 = new RecordingCanvas(640, 360);
        cd.render(cv2, 0f);
        check(cv2.commands().isEmpty() && !cd.isActive(), "expires and clears");

        // 5. Events
        EventBus bus = new EventBus(); List<String> order = new ArrayList<>();
        bus.subscribe(PlatformEvents.PlayerChat.class, Priority.LOW, e -> order.add("low"));
        bus.subscribe(PlatformEvents.PlayerChat.class, Priority.HIGHEST, e -> { order.add("hi"); e.text(e.text() + "!"); });
        bus.subscribe(PlatformEvents.PlayerChat.class, e -> { throw new RuntimeException("boom"); });
        bus.errorHandler(th -> order.add("err"));
        PlatformEvents.PlayerChat ev = bus.post(new PlatformEvents.PlayerChat(p, "yo"));
        check(order.equals(List.of("hi", "err", "low")) && ev.text().equals("yo!"), "event priority + error isolation: " + order);
        EventBus b2 = new EventBus();
        DamageGuard.install(b2);
        DamageInterceptorRegistry.clear();
        DamageInterceptorRegistry.register("g", (v, s2, amt, cx) -> cx.damageTypeId().equals("fall"));
        DamageRef fall = new DamageRef() { public Object raw() { return this; } public String typeId() { return "fall"; }
            public Optional<EntityRef> attacker() { return Optional.empty(); } public Optional<EntityRef> directEntity() { return Optional.empty(); }
            public boolean projectile() { return false; } };
        check(b2.post(new PlatformEvents.PlayerDamage(p, fall, 3f)).isCancelled(), "DamageGuard cancels via event bus");

        // 6. Packets: roundtrip + wire ids
        ShaurmaPackets.ensureLoaded();
        MemoryBuffer buf = new MemoryBuffer();
        ChatSendPacket.CODEC.encode(new ChatSendPacket("hello", null), buf);
        ChatSendPacket got = ChatSendPacket.CODEC.decode(buf);
        check(got.text().equals("hello") && got.channelId().equals(dev.shaurmalib.common.chat.ChatChannel.GLOBAL_ID), "ChatSendPacket roundtrip + default channel");
        MemoryBuffer b3 = new MemoryBuffer();
        StaminaSyncPacket sp = new StaminaSyncPacket(true, 12.5f, 20f, true, false);
        StaminaSyncPacket.CODEC.encode(sp, b3);
        check(StaminaSyncPacket.CODEC.decode(b3).equals(sp), "StaminaSyncPacket roundtrip");
        check(PacketRegistry.byClass(ChatSendPacket.class).id() == 1 && PacketRegistry.byClass(StaminaSyncPacket.class).id() == 13, "wire ids match legacy (1, 13)");
        try { PacketRegistry.register(new PacketType<>(1, "dup", String.class, null, PacketType.Direction.S2C)); check(false, "dup id rejected"); }
        catch (IllegalStateException ex) { check(true, "dup id rejected"); }
        MemoryBuffer b4 = new MemoryBuffer(); b4.writeVarInt(300); b4.writeUUID(p.uuid());
        check(b4.readVarInt() == 300 && b4.readUUID().equals(p.uuid()), "varint + uuid");

        StaminaSmokeTest.run();
        try { dev.shaurmalib.meta.MetaSmokeTest.run(); } catch (Exception e) { throw new RuntimeException(e); }

        System.out.println(failed == 0 ? "\nALL PASSED" : "\nFAILED: " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
