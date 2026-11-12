# Blockbench-плагін «Shaurma Animations»

Встановлення: Blockbench → File → Plugins → *Load Plugin from File* → `shaurma_anim.js`.

| Дія | Де |
|---|---|
| Export Shaurma Animation | File → Export |
| Shaurma: налаштування анімації (шар, маска, fade, погляд, перша особа) | Animation |
| Shaurma: налаштування пропса (ховати предмет у руці, світіння, предмет гри) | ПКМ на групі / Edit |
| Shaurma: створити риг гравця | Animation |

Пропс = група під кісткою ригу (`right_item`, `head`…), яка сама не є кісткою ригу.
Експорт пише в обрану папку `resources/`:
`assets/<ns>/shaurma_anim/player/<файл>.json`, `assets/<ns>/shaurma_anim/props/<пропс>.geo.json`,
`assets/<ns>/textures/shaurma_anim/<пропс>.png`.

Тест без Blockbench: `node test/export.test.js <папка>` (18 перевірок), потім
`java dev.shaurmalib.anim.PluginExportCheck <папка>` — вихід читає той самий рушій, що й гра.
Повна документація: `../../docs/ANIMATION.md`.
