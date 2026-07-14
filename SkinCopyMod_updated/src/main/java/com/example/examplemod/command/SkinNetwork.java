package com.example.examplemod.command;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server <-> Client arası skin onay penceresi için kullanılan network kanalı.
 * ExampleMod.preInit() içinde register() çağrılmalı.
 */
public class SkinNetwork {

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("copyskin_ch");

    public static void register() {
        int id = 0;
        NETWORK.registerMessage(OpenSkinConfirmMessage.Handler.class, OpenSkinConfirmMessage.class, id++, Side.CLIENT);
        NETWORK.registerMessage(ConfirmSkinMessage.Handler.class, ConfirmSkinMessage.class, id++, Side.SERVER);
    }
}
