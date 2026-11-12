package dev.shaurmalib.anim.runtime;

import java.util.UUID;

/**
 * Сутність, яку анімує рушій, очима умов {@link AnimBinding}. {@link #entity()} — сирий об'єкт гри
 * ({@code Player}, моб): моди приводять тип самі, ядро про нього не знає.
 */
public interface AnimSubject {
    UUID uuid();

    Object entity();

    AnimInputs inputs();

    Animator animator();
}
