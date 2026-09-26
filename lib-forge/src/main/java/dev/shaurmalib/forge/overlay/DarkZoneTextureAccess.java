package dev.shaurmalib.forge.overlay;

/**
 * Міксується в {@code DynamicTexture} ({@code MixinDynamicTextureDarkZone}).
 * Позначає КОНКРЕТНИЙ інстанс текстури як lightmap гри — щоб затемнення
 * зачіпало лише 16×16 lightmap-піксели, а не довільну текстуру, чий
 * {@code upload()} випадково спрацював у той самий момент.
 * <p>
 * Той самий прийом, що {@code TextureAccess}/{@code LightmapAccess} у
 * True Darkness (grondag) — {@code MixinLightTexture} викликає
 * {@link #darkzone_markAsLightmap()} рівно один раз одразу після
 * створення {@code LightTexture}.
 */
public interface DarkZoneTextureAccess {
    void darkzone_markAsLightmap();
}
