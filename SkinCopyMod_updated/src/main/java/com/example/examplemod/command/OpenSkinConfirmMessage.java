package com.example.examplemod.command;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Sunucudan komutu çalıştıran oyuncuya gönderilir: "bu URL için onay penceresini aç". */
public class OpenSkinConfirmMessage implements IMessage {

    private String targetPlayerName;
    private String skinUrl;

    public OpenSkinConfirmMessage() {
        // IMessage için boş constructor gerekli
    }

    public OpenSkinConfirmMessage(String targetPlayerName, String skinUrl) {
        this.targetPlayerName = targetPlayerName;
        this.skinUrl = skinUrl;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, targetPlayerName);
        ByteBufUtils.writeUTF8String(buf, skinUrl);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetPlayerName = ByteBufUtils.readUTF8String(buf);
        skinUrl = ByteBufUtils.readUTF8String(buf);
    }

    /**
     * Bu handler sadece CLIENT tarafında çalışır. GuiScreen'e referans verdiği için
     * @SideOnly ile işaretli — dedicated server'da bu sınıf hiç yüklenmeye çalışılmaz.
     */
    public static class Handler implements IMessageHandler<OpenSkinConfirmMessage, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OpenSkinConfirmMessage message, MessageContext ctx) {
            // Ağ thread'inde çalışıyoruz; GUI açmak için render/client thread'ine geçmemiz gerekiyor.
            Minecraft.getMinecraft().addScheduledTask(() ->
                    Minecraft.getMinecraft().displayGuiScreen(new GuiSkinConfirm(message.targetPlayerName, message.skinUrl))
            );
            return null;
        }
    }
}
