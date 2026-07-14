package com.example.examplemod.command;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Oyuncu GUI'de "Tamam"a bastığında sunucuya gönderilir: "şimdi bu skin'i gerçekten uygula". */
public class ConfirmSkinMessage implements IMessage {

    private String targetPlayerName;
    private String skinUrl;

    public ConfirmSkinMessage() {
        // IMessage için boş constructor gerekli
    }

    public ConfirmSkinMessage(String targetPlayerName, String skinUrl) {
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

    public static class Handler implements IMessageHandler<ConfirmSkinMessage, IMessage> {
        @Override
        public IMessage onMessage(ConfirmSkinMessage message, MessageContext ctx) {
            EntityPlayerMP sender = ctx.getServerHandler().player;
            MinecraftServer server = sender.mcServer;

            // Ağ thread'indeyiz; sunucu/dünya işlemleri için ana sunucu thread'ine geçiyoruz.
            server.addScheduledTask(() -> {
                EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(message.targetPlayerName);
                if (target == null) {
                    sender.sendMessage(new TextComponentString("Hedef oyuncu artık çevrimiçi değil: " + message.targetPlayerName));
                    return;
                }
                SkinUtils.copyFromUrl(server, target, message.skinUrl);
                sender.sendMessage(new TextComponentString(message.targetPlayerName + " için yeni skin uygulandı."));
            });

            return null;
        }
    }
}
