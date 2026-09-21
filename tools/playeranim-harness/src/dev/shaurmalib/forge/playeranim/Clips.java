package dev.shaurmalib.forge.playeranim;

import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;

/** Будівельник кліпів для тестів: права рука має константний pitch = value на всю довжину. */
final class Clips {
    private Clips() {}

    static KeyframeAnimation arm(int beginTick, int endTick, boolean loop, float value) {
        KeyframeAnimation.AnimationBuilder b = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);
        b.beginTick = beginTick;
        b.endTick = endTick;
        b.isLooped = loop;
        b.returnTick = 0;
        b.rightArm.pitch.addKeyFrame(0, value, Ease.LINEAR);
        b.rightArm.pitch.addKeyFrame(endTick, value, Ease.LINEAR);
        b.fullyEnableParts();
        return b.build();
    }
}
