package dev.shaurmalib.common.config;

import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кеш-читач YAML-файлів з диска — перенесення {@code readYamlCached}/
 * {@code fnv1aHash}/{@code invalidateCache} з оригінального
 * {@code core.config.ConfigLoader} snipers_shaurma (848 рядків), знайдена
 * прогалина: {@link ShaurmaConfigTree} узагальнює лише КОПІЮВАННЯ
 * дефолтів у файлову структуру, а {@link YamlConfigSection} — лише
 * обгортку над УЖЕ спарсеною {@code Map}, тому власне читання й
 * кешування YAML з диска, яке в оригіналі гарантувало, що
 * {@code reloadAll()} на кожному старті матчу не перепарсовує файли, які
 * ніхто не чіпав, у бібліотеці був відсутній до цього класу.
 * <p>
 * <b>Стратегія кешу — 1:1 з оригіналом:</b> кеш звіряється за
 * (mtime, розмір, вміст-хеш) файлу на диску при КОЖНОМУ читанні — це
 * НЕ "кеш на весь запуск сервера": якщо адмін вручну відредагував файл,
 * mtime/розмір/хеш зміняться, і файл буде перечитано й перепарсено
 * по-справжньому. Кеш прибирає лише повторний парсинг файлу, який
 * ніхто не чіпав з диску відколи бібліотека його востаннє бачила.
 * Хеш вмісту (FNV-1a, той самий алгоритм і константи, що в оригіналі)
 * — страховка на файлових системах із секундною точністю mtime, де файл
 * могли переписати двічі в межах тієї ж секунди з тим самим розміром.
 * <p>
 * Один екземпляр на {@link ShaurmaConfigTree}/споживача (не статичний
 * реєстр) — той самий принцип instance-based дизайну, що
 * {@link ConfigReloadBus}, щоб кілька namespace-ів (наприклад snipers і
 * maniac в одному тестовому середовищі) не ділили один кеш.
 */
public final class CachedYamlLoader {

    private final Map<Path, CachedYaml> cache = new ConcurrentHashMap<>();

    private record CachedYaml(long mtimeMillis, long size, long contentHash, Map<String, Object> data) {}

    /**
     * Читає YAML-файл з диска, використовуючи кеш за (mtime, size, хеш
     * вмісту) — та сама логіка, що {@code ConfigLoader.readYamlCached}.
     * Диск все одно один раз читається для перевірки хешу навіть при
     * влучанні в кеш (дешевше за повний SnakeYAML-парсинг, але не
     * безкоштовно) — той самий компроміс, що в оригіналі.
     *
     * @param filePath шлях до .yml-файлу на диску.
     * @param yaml     екземпляр {@link Yaml} споживача (SnakeYAML
     *                 інстанс не thread-safe для складної конфігурації
     *                 форматування, тому передається ззовні, як в
     *                 оригіналі, а не тримається одним статичним полем).
     * @return розпарсена мапа, або {@code null}, якщо файл порожній.
     * @throws IOException якщо файл не вдалось прочитати з диска.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readYamlCached(Path filePath, Yaml yaml) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(filePath).toMillis();
        } catch (IOException e) {
            mtime = -1;
        }
        long size = bytes.length;
        long contentHash = fnv1aHash(bytes);

        CachedYaml cached = cache.get(filePath);
        if (cached != null && cached.mtimeMillis() == mtime && cached.size() == size
                && cached.contentHash() == contentHash) {
            return cached.data();
        }

        Map<String, Object> data;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            data = yaml.load(is);
        }
        if (data != null) {
            cache.put(filePath, new CachedYaml(mtime, size, contentHash, data));
        } else {
            cache.remove(filePath);
        }
        return data;
    }

    /**
     * Швидкий 64-бітний хеш вмісту файлу (FNV-1a) — лише для виявлення
     * змін, не криптографічний. Ті самі константи, що в оригіналі.
     */
    public static long fnv1aHash(byte[] bytes) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= (b & 0xffL);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** Прибирає файл з кешу, щоб наступне читання гарантовано пішло на диск (наприклад одразу після ручного {@code saveYaml}). */
    public void invalidate(Path filePath) {
        cache.remove(filePath);
    }

    /** Повністю очищує кеш — типово при {@code reloadAll()}, якщо споживач хоче гарантовано перечитати все з диска. */
    public void invalidateAll() {
        cache.clear();
    }
}
