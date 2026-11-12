package dev.shaurmalib.meta.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Розділ магазину. Звичайний — показує всі свої пропозиції завжди («За уламки», «Скрині»). Ротаційний
 * ({@code rotationHours > 0}) — щоперіоду вибирає {@code size} пропозицій зі свого пулу з випадковою знижкою
 * («Пропозиції дня» з таймером, «Нічний ринок» на тиждень). Вибір — чиста функція часу й id режиму, тож
 * сервер і клієнт бачать одне й те саме без пакетів; ціну при купівлі все одно рахує сервер.
 */
public record ShopSection(String id, String title, int rotationHours, int size, int minDiscount, int maxDiscount) {

    /** Пропозиція з поточною ціною. */
    public record Priced(ShopOffer offer, long price, long oldPrice, int discount) {}

    public static ShopSection fixed(String id, String title) { return new ShopSection(id, title, 0, 0, 0, 0); }

    public static ShopSection rotating(String id, String title, int hours, int size, int minDiscount, int maxDiscount) {
        return new ShopSection(id, title, hours, size, minDiscount, maxDiscount);
    }

    public boolean rotating() { return rotationHours > 0; }

    public long periodMs() { return rotationHours * 3_600_000L; }

    public long period(long now) { return rotating() ? Math.floorDiv(now, periodMs()) : 0; }

    /** Коли зміниться асортимент (мс epoch); 0 — ніколи. */
    public long endsAt(long now) { return rotating() ? (period(now) + 1) * periodMs() : 0; }

    public List<Priced> current(String modeId, List<ShopOffer> all, long now) {
        List<ShopOffer> pool = new ArrayList<>();
        for (ShopOffer o : all) if (id.equals(o.section())) pool.add(o);
        List<Priced> out = new ArrayList<>();
        if (!rotating()) {
            for (ShopOffer o : pool) out.add(priced(o, o.discount()));
            return out;
        }
        Random r = new Random(31L * (modeId + ":" + id).hashCode() + period(now) * 7919L);
        List<ShopOffer> left = new ArrayList<>(pool);
        for (int i = 0; i < size && !left.isEmpty(); i++) {
            ShopOffer o = left.remove(r.nextInt(left.size()));
            int d = maxDiscount <= minDiscount ? minDiscount : minDiscount + r.nextInt(maxDiscount - minDiscount + 1);
            d = Math.round(d / 5f) * 5;   // знижки кратні 5 — як у живих магазинах
            out.add(priced(o, Math.max(o.discount(), d)));
        }
        return out;
    }

    private static Priced priced(ShopOffer o, int discount) {
        long price = discount <= 0 ? o.price() : Math.max(1, Math.round(o.price() * (100 - discount) / 100.0));
        return new Priced(o, price, o.price(), discount);
    }
}
