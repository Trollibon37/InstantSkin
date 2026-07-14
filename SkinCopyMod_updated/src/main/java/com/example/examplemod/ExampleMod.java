package com.example.examplemod;

import com.example.examplemod.command.CommandCopySkin;
import com.example.examplemod.command.SkinEventHandler;
import com.example.examplemod.command.SkinNetwork;
import com.example.examplemod.command.SkinPersistence;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = ExampleMod.MODID, name = ExampleMod.NAME, version = ExampleMod.VERSION)
public class ExampleMod
{
    public static final String MODID = "examplemod";
    public static final String NAME = "Example Mod";
    public static final String VERSION = "1.0";

    private static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();

        // Skin onay penceresi (GUI) için server<->client network kanalını kaydet
        SkinNetwork.register();
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        // some example code
        logger.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());

        // /copyskin komutu için oyuncu login olayını dinleyen handler'ı kaydet
        MinecraftForge.EVENT_BUS.register(new SkinEventHandler());
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event)
    {
        // Kayıtlı skin verisini (config/copyskin_data.json) yükle
        SkinPersistence.load();
        // /copyskin komutunu sunucuya kaydet
        event.registerServerCommand(new CommandCopySkin());
    }
}
