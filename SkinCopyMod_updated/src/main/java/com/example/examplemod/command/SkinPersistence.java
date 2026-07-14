package com.example.examplemod.command;
// NOT: Paket adını kendi projenin gerçek modid paketine göre değiştir

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Oyuncuların özel skin'lerini config/copyskin_data.json dosyasında saklar.
 * Böylece oyuncu relog olduğunda ya da sunucu yeniden başladığında
 * verilen skin kaybolmaz.
 */
public class SkinPersistence {

    public static class StoredSkin {
        public String value;      // base64 texture değeri
        public String signature;  // Mojang imzası (url modunda null olabilir)

        public StoredSkin(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StoredSkin>>() {}.getType();

    private static File file;
    private static Map<String, StoredSkin> data = new HashMap<>();

    /** Sunucu başlarken (FMLServerStartingEvent) çağrılmalı. */
    public static void load() {
        file = new File(Loader.instance().getConfigDir(), "copyskin_data.json");
        if (!file.exists()) {
            data = new HashMap<>();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Map<String, StoredSkin> loaded = GSON.fromJson(reader, MAP_TYPE);
            data = (loaded != null) ? loaded : new HashMap<>();
        } catch (Exception e) {
            data = new HashMap<>();
            System.err.println("[CopySkin] Kayıtlı skin verisi okunamadı: " + e.getMessage());
        }
    }

    public static void save() {
        if (file == null) return;
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, MAP_TYPE, writer);
        } catch (Exception e) {
            System.err.println("[CopySkin] Skin verisi kaydedilemedi: " + e.getMessage());
        }
    }

    public static void set(UUID playerUuid, String value, String signature) {
        data.put(playerUuid.toString(), new StoredSkin(value, signature));
        save();
    }

    public static void remove(UUID playerUuid) {
        data.remove(playerUuid.toString());
        save();
    }

    public static StoredSkin get(UUID playerUuid) {
        return data.get(playerUuid.toString());
    }
}
