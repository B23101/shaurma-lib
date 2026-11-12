package dev.shaurmalib.common.offline;

import java.util.Objects;
import java.util.UUID;

/**
 * Токен відкритого scope офлайн-присутності (план, §4.1). Кожне тіло
 * зберігає його в NBT: тіло зі старим або відсутнім токеном вважається
 * мертвим і прибирається безшумно — це страхує рестарти сервера й падіння.
 */
public record OfflineScopeId(UUID value) {

    public OfflineScopeId {
        Objects.requireNonNull(value, "value");
    }

    public static OfflineScopeId newId() {
        return new OfflineScopeId(UUID.randomUUID());
    }
}
