package dev.shaurmalib.forge.license;

import dev.shaurmalib.common.license.LicenseGate;
import dev.shaurmalib.common.license.LicenseProvider;

import java.nio.file.Path;

/**
 * Робочий license-модуль (п. 3.31 плану). Створюється лише якщо споживач явно
 * викликав {@code ShaurmaLib.Builder.withLicense(...)} і передав СВОЮ
 * реалізацію {@link LicenseProvider} — бібліотека ніколи не постачає
 * дефолтний провайдер із вбудованим ключем/секретом.
 */
public final class LicenseModule {

    private final LicenseGate gate;

    public LicenseModule(LicenseProvider provider, Path licenseDir) {
        this.gate = new LicenseGate(provider, licenseDir);
    }

    public LicenseGate gate() {
        return gate;
    }
}
