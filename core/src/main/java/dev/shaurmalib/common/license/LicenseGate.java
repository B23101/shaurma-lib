package dev.shaurmalib.common.license;

import java.nio.file.Path;

/**
 * Каркас ліцензійного гейта (план, п. 3.31) — обгортка над
 * споживач-наданим {@link LicenseProvider}. Бібліотека надає лише точку
 * підключення (щоб {@code ShaurmaCommandRoot} з майбутнього Етапу 2 міг
 * автоматично блокувати чутливі підкоманди, поки
 * {@code !LicenseGate.isActivated()}), не сам ключ/секрет — див. докстрінг
 * {@link LicenseProvider} для пояснення чому.
 */
public final class LicenseGate {

    private final LicenseProvider provider;
    private final Path licenseDir;

    public LicenseGate(LicenseProvider provider, Path licenseDir) {
        this.provider = provider;
        this.licenseDir = licenseDir;
    }

    /** Викликається один раз при старті сервера — делегує до {@link LicenseProvider#init}. */
    public void init() {
        provider.init(licenseDir);
    }

    public boolean isActivated() {
        return provider.isActivated();
    }

    public boolean activate(String key) {
        return provider.activate(licenseDir, key);
    }

    public String displayId() {
        return provider.displayId();
    }

    /**
     * Зручний метод-гейт для команд/дій, які мають бути заблоковані без
     * активної ліцензії — кидає {@link IllegalStateException} з
     * повідомленням, придатним для показу в чаті гравцю/адміну.
     */
    public void requireActivated(String actionDescription) {
        if (!isActivated()) {
            throw new IllegalStateException(
                    "Ліцензія не активована — дія недоступна: " + actionDescription);
        }
    }
}
