package dev.shaurmalib.anim.mc.emote;

import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.emote.Emote;
import dev.shaurmalib.anim.mc.McAnim;
import dev.shaurmalib.anim.rig.Rig;
import dev.shaurmalib.anim.runtime.AnimInputs;
import dev.shaurmalib.anim.runtime.Animator;
import dev.shaurmalib.anim.runtime.PlayRequest;
import dev.shaurmalib.anim.runtime.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * 3D-превʼю емоції на моделі самого гравця (його скін, броня, предмет у руці).
 *
 * Окремий {@link Animator} — не той, що в світі: колесо показує наведену емоцію, а справжній гравець
 * у цей час стоїть як стояв. Малюється ванільним {@link InventoryScreen#renderEntityInInventory}, тобто
 * через звичайний {@code PlayerRenderer} з міксинами рушія — ті самі маски, шари, пропси, що й у грі.
 */
public final class EmotePreview {

    /** Пауза між повторами одноразової емоції у превʼю, с. */
    private static final float REPLAY_PAUSE = 0.6f;

    private final Animator animator = new Animator(UUID.randomUUID(), Rig.PLAYER, ShaurmaAnim.clips());
    private Emote current;
    private int ticks, replayAt = -1;

    public EmotePreview() {
        // база під емоцією — та сама локомоція, що в грі (стояння рушія або ванільна поза)
        if (ShaurmaAnim.playerLocomotion()) animator.locomotion(ShaurmaAnim.defaultPlayerLocomotion());
    }

    public Animator animator() { return animator; }

    public Emote current() { return current; }

    /** Показати емоцію (null — стояти). Та сама — продовжує, без перезапуску. */
    public void show(Emote e) {
        if (e == current) return;
        current = e;
        if (e == null) {
            animator.stop(Slot.ACTION, 0.2f);
            replayAt = -1;
            return;
        }
        start();
    }

    private void start() {
        AnimationClip clip = ShaurmaAnim.clip(current.clip());
        PlayRequest r = PlayRequest.of(current.clip()).slot(Slot.ACTION).mask(current.mask()).restart(true).fade(0.12f, 0.2f)
            .tag("preview");
        if (current.loop() != null) r.loop(current.loop() ? AnimationClip.LoopMode.LOOP : AnimationClip.LoopMode.ONCE);
        animator.play(r);
        boolean loops = current.loop() != null ? current.loop() : clip != null && clip.loop() == AnimationClip.LoopMode.LOOP;
        replayAt = loops || clip == null ? -1 : ticks + Math.round((clip.length() + REPLAY_PAUSE) * 20);
    }

    /** З {@code Screen.tick} — 20 разів на секунду, як і аніматори світу. */
    public void tick() {
        ticks++;
        AnimInputs in = animator.inputs();
        in.onGround = true;
        in.sneaking = in.sprinting = in.swimming = in.crawling = in.riding = in.climbing = in.flying = false;
        in.usingItem = in.swinging = in.sleeping = in.dead = false;
        in.horizontalSpeed = in.verticalSpeed = in.fallDistance = 0;
        in.ageTicks = ticks;
        animator.tick(null, null, null); // без правил і подій: превʼю не грає звуків і не тягне чужі пози
        if (current != null && replayAt >= 0 && ticks >= replayAt) start();
    }

    /**
     * @param x,yFeet точка ступнів на екрані
     * @param scale   пікселів на блок
     * @param yaw     поворот моделі (0 — обличчям до гравця)
     */
    public void render(GuiGraphics g, LivingEntity entity, int x, int yFeet, int scale, float yaw) {
        renderEntity(g, entity, animator, x, yFeet, scale, yaw, Minecraft.getInstance().getFrameTime());
    }

    /** Як ванільний {@code renderEntityInInventoryFollowsMouse}, але з фіксованим поворотом і аніматором превʼю. */
    public static void renderEntity(GuiGraphics g, LivingEntity e, Animator animator, int x, int yFeet, int scale,
                                    float yaw, float partial) {
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cam = new Quaternionf().rotateX(-8f * Mth.DEG_TO_RAD);
        pose.mul(cam);
        float bodyRot = e.yBodyRot, bodyRotO = e.yBodyRotO, yRot = e.getYRot(), yRotO = e.yRotO;
        float xRot = e.getXRot(), xRotO = e.xRotO, head = e.yHeadRot, headO = e.yHeadRotO;
        float facing = 180f + yaw;
        e.yBodyRot = e.yBodyRotO = facing;
        e.setYRot(facing);
        e.yRotO = facing;
        e.setXRot(0);
        e.xRotO = 0;
        e.yHeadRot = e.yHeadRotO = facing;
        try {
            McAnim.withPreview(e, animator, partial, () -> InventoryScreen.renderEntityInInventory(g, x, yFeet, scale, pose, cam, e));
        } finally {
            e.yBodyRot = bodyRot;
            e.yBodyRotO = bodyRotO;
            e.setYRot(yRot);
            e.yRotO = yRotO;
            e.setXRot(xRot);
            e.xRotO = xRotO;
            e.yHeadRot = head;
            e.yHeadRotO = headO;
        }
    }
}
