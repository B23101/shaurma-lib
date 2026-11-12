# Екранні ефекти (`dev.shaurmalib.forge.fx.screen`)

Двигун перенесено з EnhancedVisuals у snipers_shaurma. Що він показує поверх HUD:

* **кров / іній / пил**: текстури розкидані по краях екрана й поступово згасають;
* **спалах**: кольорова заливка всього екрана, що згасає;
* **розмиття**: власний `PostChain` на `blur.json`, тому не конфліктує з ванільними шейдерами й шейдерами режимів;
* **серцебиття** на низькому здоров'ї: вінетка, розмиття і звук;
* **звук** у вухах гравця (`shaurma_lib:screenfx_ringing`, `screenfx_ringing_short`, `screenfx_heartbeat_in/out`).

Ефектів середовища (дощ, пісок, бризки води) тут **немає**: вони лишаються в режимі, якому потрібні.

## Клієнт

```java
ScreenFx.register("mymod:hit", ScreenFxPreset.builder()
    .spray(ScreenFxTextures.SLASH, 26, 128, 160, 420, 0.55, 0x4D0303)  // текстури, к-сть, розмір, життя від/до (тіки), прозорість, колір
    .flash(0x7A0000, 0.4f, 10)                                         // колір, прозорість, тіки
    .blur(28f, 10)                                                     // радіус, тіки
    .sound(new ResourceLocation("shaurma_lib", "screenfx_ringing_short"), 0.8f, 1f)
    .build());
ScreenFx.setActiveGate(() -> MyState.inMatch());   // коли ефекти дозволені
ScreenFx.enableHeartbeat(ScreenFxHeartbeat.Settings.defaults(), null); // null = ванільне здоров'я
```

Оверлей, тік, розмиття й очищення при виході зі світу бібліотека підключає сама (`ScreenFxClient`).
Вбудовані пресети: `shaurma_lib:blood_slash|blood_impact|blood_pierce|blood_splatter|fire|freeze|explosion`.
Текстури лежать у `assets/shaurma_lib/textures/screenfx/`. Свої можна покласти у `assets/<ns>/textures/screenfx/` і взяти через `ScreenFxTextures.of(ns, папка, імена…)`.

## Сервер

```java
ScreenFxServer.play(player, "mymod:hit", 1f);                    // intensity множить кількість, яскравість і розмиття
ScreenFxServer.enableVanillaDamage(p -> Game.isActive());          // авто: кров від ванільного урону + пил від вибухів
```

Приклад режиму — `maniac_shaurma/.../client/fx/ManiacScreenFx.java`.
