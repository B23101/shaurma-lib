package dev.shaurmalib.forge.client.offline;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import dev.shaurmalib.forge.offline.OfflineAvatarBase;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Скін тіла на клієнті (план, §5.12). Гравець, що вийшов, зникає з таб-листа
 * клієнтів, тож скін за UUID не взяти: {@code GameProfile} будується зі
 * синхронізованого {@code textures}+підпису, а {@code SkinManager} завантажує
 * текстуру асинхронно. Поки вона вантажиться або {@code textures} порожній
 * (offline-mode сервера), показується дефолтний скін, як у гравця.
 * <p>
 * Перенесено з {@code PersistentPlayerRenderer} (Open-Persistence) без зміни
 * логіки; доданий лише кеш результату, щоб не розбирати base64-JSON кожен кадр.
 * Викликається лише з render-потоку клієнта.
 * <p>
 * <b>Походження:</b> Логіка скіна з {@code PersistentPlayerRenderer} з Open-Persistence (com.norwood.openpersistence,
 * 1.20.1; у його build.gradle.kts заявлено GPL-3.0). Питання ліцензії
 * перенесеного коду відкрите — див. план, §12.4.
 */
@OnlyIn(Dist.CLIENT)
public final class OfflineSkinResolver {

    private record Resolved(ResourceLocation skin, boolean slim) {}

    private static final int CACHE_LIMIT = 64;
    private static final Map<String, Resolved> CACHE = new HashMap<>();

    private OfflineSkinResolver() {}

    public static ResourceLocation skinLocation(OfflineAvatarBase entity) {
        UUID uuid = entity.getPlayerUUID().orElse(Util.NIL_UUID);
        Minecraft minecraft = Minecraft.getInstance();
        PlayerInfo info = playerInfo(minecraft, uuid);
        if (info != null) {
            return info.getSkinLocation();
        }
        return resolve(minecraft, entity, uuid).skin();
    }

    public static boolean isSlim(OfflineAvatarBase entity) {
        UUID uuid = entity.getPlayerUUID().orElse(Util.NIL_UUID);
        Minecraft minecraft = Minecraft.getInstance();
        PlayerInfo info = playerInfo(minecraft, uuid);
        if (info != null) {
            return "slim".equals(info.getModelName());
        }
        return resolve(minecraft, entity, uuid).slim();
    }

    private static PlayerInfo playerInfo(Minecraft minecraft, UUID uuid) {
        return minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(uuid);
    }

    private static Resolved resolve(Minecraft minecraft, OfflineAvatarBase entity, UUID uuid) {
        String key = uuid + "|" + entity.getSkinTexture();
        Resolved cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        GameProfile profile = profileWithSkin(entity, uuid);
        ResourceLocation skin = minecraft.getSkinManager().getInsecureSkinLocation(profile);
        MinecraftProfileTexture texture = minecraft.getSkinManager()
                .getInsecureSkinInformation(profile).get(MinecraftProfileTexture.Type.SKIN);
        boolean slim = texture != null
                ? "slim".equals(texture.getMetadata("model"))
                : "slim".equals(DefaultPlayerSkin.getSkinModelName(uuid));
        Resolved resolved = new Resolved(skin, slim);
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(key, resolved);
        return resolved;
    }

    /**
     * Перебудовує {@link GameProfile} гравця, повертаючи властивість {@code textures},
     * зняту при виході. Без неї пошук скіна бачить лише голі uuid/ім'я й віддає дефолтний.
     */
    private static GameProfile profileWithSkin(OfflineAvatarBase entity, UUID uuid) {
        GameProfile profile = new GameProfile(uuid, entity.getPlayerName());
        String value = entity.getSkinTexture();
        if (!value.isEmpty()) {
            String signature = entity.getSkinSignature();
            profile.getProperties().put("textures",
                    new Property("textures", value, signature.isEmpty() ? null : signature));
        }
        return profile;
    }
}
