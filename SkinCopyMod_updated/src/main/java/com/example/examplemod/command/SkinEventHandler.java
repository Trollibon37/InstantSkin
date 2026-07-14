package com.example.examplemod.command;
// NOT: Paket adını kendi projenin gerçek modid paketine göre değiştir

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Bu sınıfı ana mod sınıfında MinecraftForge.EVENT_BUS.register(new SkinEventHandler())
 * ile kaydetmen gerekiyor. Oyuncu sunucuya her girdiğinde, kayıtlı özel bir skin'i
 * varsa otomatik olarak tekrar uygular.
 */
public class SkinEventHandler {

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;

        SkinPersistence.StoredSkin stored = SkinPersistence.get(player.getUniqueID());
        if (stored == null) {
            return; // Bu oyuncu için kayıtlı özel bir skin yok, hiçbir şey yapma
        }

        GameProfile profile = player.getGameProfile();
        PropertyMap properties = profile.getProperties();
        properties.removeAll("textures");
        properties.put("textures", new Property("textures", stored.value, stored.signature));

        // player.mcServer, EntityPlayerMP'nin bağlı olduğu sunucu instance'ıdır (1.12.2)
        SkinUtils.broadcastSkinUpdate(player.mcServer, player);
    }
}
