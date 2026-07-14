package com.example.examplemod.command;
// NOT: Paket adını kendi projenin gerçek modid paketine göre değiştir
// (örn. com.senin.modun.command)

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketSetExperience;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Oyuncu skin'lerini sunucu tarafında (server-authoritative) değiştirmek için
 * yardımcı fonksiyonlar. CustomSkinLoader gerektirmez.
 */
public class SkinUtils {

    public interface SkinCallback {
        void onResult(boolean success, String errorMessage);
    }

    /**
     * Zaten bu sunucuda çevrimiçi olan başka bir oyuncunun GameProfile'ından
     * texture bilgisini doğrudan kopyalar (Mojang'a gitmeye gerek yok).
     */
    public static void copyFromGameProfile(MinecraftServer server, EntityPlayerMP target, GameProfile sourceProfile) {
        PropertyMap sourceProps = sourceProfile.getProperties();
        if (!sourceProps.containsKey("textures")) {
            return; // Kaynak oyuncunun özel bir texture'ı yok (varsayılan Steve/Alex olabilir)
        }
        Property sourceTexture = sourceProps.get("textures").iterator().next();
        applyTextureProperty(server, target, new Property("textures", sourceTexture.getValue(), sourceTexture.getSignature()));
    }

    /**
     * Doğrudan bir URL'den (imzasız/unsigned) yeni bir skin texture'ı oluşturup uygular.
     * Mojang'ın gerçek profil formatını birebir taklit ediyoruz (profileId, profileName,
     * timestamp dahil) çünkü CustomSkinLoader gibi bazı client mod'ları eksik alanlı JSON'ı
     * geçersiz sayıp görmezden gelebiliyor.
     */
    public static void copyFromUrl(MinecraftServer server, EntityPlayerMP target, String skinUrl) {
        String uuidNoDashes = target.getUniqueID().toString().replace("-", "");
        String json = "{"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"profileId\":\"" + uuidNoDashes + "\","
                + "\"profileName\":\"" + target.getName() + "\","
                + "\"signatureRequired\":false,"
                + "\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}"
                + "}";
        String base64Value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        applyTextureProperty(server, target, new Property("textures", base64Value));
    }

    /**
     * Kullanıcı adına göre Mojang API'sinden (çevrimdışı/başka sunucudaki) bir hesabın
     * UUID + texture bilgisini ASENKRON olarak çeker (sunucu thread'ini kilitlememek için).
     * Sonuç, ana sunucu thread'inde callback ile döner.
     */
    public static void copyFromMojangUsernameAsync(MinecraftServer server, EntityPlayerMP target, String username, SkinCallback callback) {
        new Thread(() -> {
            try {
                String uuid = fetchUuidFromUsername(username);
                if (uuid == null) {
                    server.addScheduledTask(() -> callback.onResult(false, "Bu kullanıcı adına ait bir Minecraft hesabı bulunamadı: " + username));
                    return;
                }

                Property texture = fetchTextureProperty(uuid);
                if (texture == null) {
                    server.addScheduledTask(() -> callback.onResult(false, uuid + " için texture bilgisi alınamadı (özel skin'i olmayabilir)."));
                    return;
                }

                server.addScheduledTask(() -> {
                    applyTextureProperty(server, target, texture);
                    callback.onResult(true, null);
                });
            } catch (Exception e) {
                server.addScheduledTask(() -> callback.onResult(false, "Mojang API hatası: " + e.getMessage()));
            }
        }, "CopySkin-MojangLookup").start();
    }

    private static String fetchUuidFromUsername(String username) throws Exception {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) {
            return null; // Kullanıcı yok
        }

        String body = readAll(conn);
        JsonObject obj = new JsonParser().parse(body).getAsJsonObject();
        return obj.get("id").getAsString();
    }

    private static Property fetchTextureProperty(String uuidNoDashes) throws Exception {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDashes + "?unsigned=false");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) {
            return null;
        }

        String body = readAll(conn);
        JsonObject obj = new JsonParser().parse(body).getAsJsonObject();
        if (!obj.has("properties") || obj.getAsJsonArray("properties").size() == 0) {
            return null;
        }
        JsonObject prop = obj.getAsJsonArray("properties").get(0).getAsJsonObject();
        String value = prop.get("value").getAsString();
        String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
        return new Property("textures", value, signature);
    }

    private static String readAll(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * Hedef oyuncunun GameProfile'ına yeni texture property'sini uygular,
     * bunu diske kaydeder (kalıcılık) ve tüm bağlı oyunculara görsel güncellemeyi yayınlar.
     */
    private static void applyTextureProperty(MinecraftServer server, EntityPlayerMP target, Property newTexture) {
        GameProfile profile = target.getGameProfile();
        PropertyMap properties = profile.getProperties();
        properties.removeAll("textures");
        properties.put("textures", newTexture);

        broadcastSkinUpdate(server, target);
        forceSelfRefresh(server, target);

        // Kalıcı olması için kaydet (relog / server restart sonrası tekrar uygulanacak)
        SkinPersistence.set(target.getUniqueID(), newTexture.getValue(), newTexture.getSignature());
    }

    /**
     * Oyuncunun GameProfile'ındaki mevcut texture'ı tüm bağlı client'lara yeniden
     * gönderir (tab listesi + diğer oyuncuların 3. şahıs görünümü için yeterli).
     * SkinEventHandler login anında sadece bunu çağırır (o an zaten client kendi
     * profiliyle taze bağlandığı için ekstra respawn'a gerek yok).
     */
    public static void broadcastSkinUpdate(MinecraftServer server, EntityPlayerMP target) {
        SPacketPlayerListItem removePacket = new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, target);
        SPacketPlayerListItem addPacket = new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, target);

        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            p.connection.sendPacket(removePacket);
            p.connection.sendPacket(addPacket);
        }
    }

    /**
     * Hedef oyuncunun kendi client'ını, aynı boyutta "sahte" bir respawn tetikleyerek
     * yeniler. Bu, oyuncu modelinin/texture'ının tamamen yeniden yüklenmesini zorlar
     * (client-side skin cache'lerini de bypass eder) — relog yapmaya gerek kalmaz.
     * SADECE komutla skin değiştirildiğinde çağrılır (login anında değil).
     *
     * NOT: Bu, hedef oyuncunun ekranında çok kısa bir "yeniden yükleniyor" titremesine
     * (relog'a benzer, ama bağlantı kopmadan) sebep olur. Anlık skin değişimi için
     * bilinen ve yaygın kullanılan bir yöntemdir.
     */
    private static void forceSelfRefresh(MinecraftServer server, EntityPlayerMP target) {
        WorldServer world = (WorldServer) target.world;

        target.connection.sendPacket(new SPacketRespawn(
                target.dimension,
                world.getDifficulty(),
                world.getWorldInfo().getTerrainType(),
                target.interactionManager.getGameType()
        ));

        server.getPlayerList().updatePermissionLevel(target);
        target.connection.setPlayerLocation(target.posX, target.posY, target.posZ, target.rotationYaw, target.rotationPitch);

        target.connection.sendPacket(new SPacketPlayerAbilities(target.capabilities));
        target.connection.sendPacket(new SPacketHeldItemChange(target.inventory.currentItem));
        server.getPlayerList().syncPlayerInventory(target);
        target.connection.sendPacket(new SPacketSetExperience(target.experience, target.experienceLevel, target.experienceTotal));

        for (PotionEffect effect : target.getActivePotionEffects()) {
            target.connection.sendPacket(new SPacketEntityEffect(target.getEntityId(), effect));
        }
    }

    /** Kayıtlı özel skin'i siler ve texture property'sini kaldırır (varsayılan görünüme döner). */
    public static void resetSkin(MinecraftServer server, EntityPlayerMP target) {
        GameProfile profile = target.getGameProfile();
        profile.getProperties().removeAll("textures");
        SkinPersistence.remove(target.getUniqueID());
        broadcastSkinUpdate(server, target);
        forceSelfRefresh(server, target);
    }
}
