#version 150

// Вбудований пресет "справжнього" затемнення для ScreenEffectPostChain
// (dev.shaurmalib.forge.fx.ScreenEffectPostChain#enableDarkness/disableDarkness).
//
// Це НЕ напівпрозорий чорний прямокутник над готовою картинкою (для того в
// бібліотеці вже є WorldTintOverlay) — тут кожен піксель вже відрендереного
// кадру світу множиться на коефіцієнт яскравості. Тому деталі зображення не
// "просвічують" крізь темряву на високій інтенсивності: замість
// напівпрозорої плівки картинка сама стає темнішою, аж до майже повної
// черноти на Intensity = 1.

uniform sampler2D DiffuseSampler;

uniform float Intensity;      // 0..1, плавно рахує ScreenEffectPostChain з PostChainFadeSpec
uniform float MinBrightness;  // залишкова яскравість на піку затемнення (0 = чорний екран)

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);

    float t = clamp(Intensity, 0.0, 1.0);
    // Нелінійна крива: перші ~30% ходу малопомітні, останні — різкий провал
    // у темряву. Відчувається як "очі не встигають", а не як лінійний діммер.
    float eased = t * t * (3.0 - 2.0 * t); // smoothstep(0, 1, t)

    float factor = mix(1.0, clamp(MinBrightness, 0.0, 1.0), eased);

    fragColor = vec4(src.rgb * factor, src.a);
}
