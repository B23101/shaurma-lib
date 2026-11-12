package dev.shaurmalib.common.fx;

/**
 * Дескриптор одного зареєстрованого повноекранного post-chain ефекту
 * (план, п. 3.23) — узагальнення {@code BlackAndWhiteScreenEffect}
 * snipers_shaurma. Чиста структура даних (0 Minecraft/Forge-імпортів);
 * сам шейдер ідентифікується namespace + шляхом окремо, дзеркально до
 * того, як оригінал будував свій {@code ResourceLocation}:
 * <pre>
 *   new ResourceLocation("snipers_shaurma", "shaders/post/bw_screen.json")
 * </pre>
 * Форge-шар ({@code dev.shaurmalib.forge.fx.ScreenEffectPostChain})
 * використовує саме двоаргументний {@code ResourceLocation(namespace, path)}
 * конструктор, а НЕ одноаргументний {@code ResourceLocation("ns:path")} —
 * навмисно, щоб уникнути мовчазної підстановки дефолтного namespace
 * {@code "minecraft"} у разі, якщо консюмер випадково передасть шлях без
 * роздільника {@code ':'}. Асет завжди лежить у namespace консюмера
 * (наприклад {@code snipers_shaurma}), НЕ бібліотеки — той самий принцип,
 * що {@code ShaurmaConfigTree}: дані завжди постачає споживач.
 *
 * @param effectId  унікальний ідентифікатор ефекту в межах консюмера
 *                  (наприклад {@code "monochrome"}) — використовується
 *                  і як ключ реєстрації, і як частина причин-множини.
 * @param namespace namespace консюмера (наприклад {@code "snipers_shaurma"}).
 * @param path      шлях ресурсу PostChain-конфігу відносно namespace
 *                  (наприклад {@code "shaders/post/bw_screen.json"}).
 */
public record PostChainEffectSpec(String effectId, String namespace, String path) {
}
