package dev.shaurmalib.common.config;

/**
 * Перенесено 1:1 з {@code org.example.snipers_shaurma.core.config.ConfigException}.
 * Ті самі два конструктори — заміна {@code import} у споживачі не міняє
 * жодного виклику-сайту.
 */
public class ConfigException extends RuntimeException {
    public ConfigException(String configFile, String field, String reason) {
        super("Config error in [" + configFile + "] field [" + field + "]: " + reason);
    }

    public ConfigException(String configFile, Throwable cause) {
        super("Failed to load config: " + configFile, cause);
    }
}
