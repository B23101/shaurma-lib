package dev.shaurmalib.meta.economy;

/**
 * Пропозиція магазину.
 *
 * @param reward   що купується (косметика — одноразово; скрині/монети/деталі — скільки завгодно)
 * @param currency чим платити
 * @param section  розділ ({@link ShopSection#id()}): «daily», «night», «shards», «chests», «catalog»…
 * @param discount постійна знижка у %, ротаційний розділ може дати більшу
 */
public record ShopOffer(String id, Reward reward, String currency, long price, String section, int discount) {

    public static final String CATALOG = "catalog";

    public ShopOffer {
        if (section == null || section.isEmpty()) section = CATALOG;
        discount = Math.max(0, Math.min(90, discount));
    }

    public static ShopOffer cosmetic(String cosmeticId, String currency, long price) {
        return new ShopOffer("cosmetic:" + cosmeticId, Reward.cosmetic(cosmeticId), currency, price, CATALOG, 0);
    }

    public static ShopOffer chest(String chestId, String currency, long price) {
        return new ShopOffer("chest:" + chestId, Reward.chest(chestId, 1), currency, price, "chests", 0);
    }

    /** Та сама пропозиція в іншому розділі (id стає унікальним для розділу). */
    public ShopOffer in(String sectionId) {
        return new ShopOffer(sectionId + ":" + id, reward, currency, price, sectionId, discount);
    }

    public ShopOffer off(int percent) { return new ShopOffer(id, reward, currency, price, section, percent); }
}
