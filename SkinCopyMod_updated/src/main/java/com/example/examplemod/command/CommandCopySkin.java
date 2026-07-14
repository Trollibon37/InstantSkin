package com.example.examplemod.command;
// NOT: Paket adını kendi projenin gerçek modid paketine göre değiştir
// (örn. com.senin.modun.command)

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Kullanım:
 *   /copyskin <hedefOyuncu> player <kaynakOyuncuAdı>   -> başka bir Minecraft hesabının skin'ini kopyalar
 *   /copyskin <hedefOyuncu> url <skinUrl>               -> verilen URL'deki PNG'yi skin olarak uygular
 *
 * Değişiklik SUNUCUDAKİ HERKESTE görünür (server-side GameProfile + broadcast).
 */
public class CommandCopySkin extends CommandBase {

    @Override
    public String getName() {
        return "copyskin";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/copyskin <hedefOyuncu> player <kaynakOyuncuAdı>  |  /copyskin <hedefOyuncu> url <skinUrl>  |  /copyskin <hedefOyuncu> reset";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // sadece op'lar kullanabilsin (permission level 2)
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }

        String targetName = args[0];
        String mode = args[1].toLowerCase();

        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetName);
        if (target == null) {
            throw new CommandException("Oyuncu bulunamadı veya çevrimdışı: " + targetName);
        }

        if (mode.equals("reset")) {
            SkinUtils.resetSkin(server, target);
            sender.sendMessage(new TextComponentString(targetName + " için özel skin kaldırıldı."));
            return;
        }

        if (args.length < 3) {
            throw new WrongUsageException(getUsage(sender));
        }
        String value = args[2];

        switch (mode) {
            case "player": {
                // Önce sunucuda çevrimiçi mi diye bak (varsa Mojang'a gitmeye gerek yok).
                EntityPlayerMP sourceOnline = server.getPlayerList().getPlayerByUsername(value);
                if (sourceOnline != null) {
                    SkinUtils.copyFromGameProfile(server, target, sourceOnline.getGameProfile());
                    sender.sendMessage(new TextComponentString(targetName + " artık " + value + " oyuncusunun (çevrimiçi) skin'ine sahip."));
                } else {
                    sender.sendMessage(new TextComponentString(value + " sunucuda çevrimdışı, Mojang'dan skin bilgisi alınıyor..."));
                    SkinUtils.copyFromMojangUsernameAsync(server, target, value, (success, errorMsg) -> {
                        if (success) {
                            sender.sendMessage(new TextComponentString(targetName + " artık " + value + " oyuncusunun skin'ine sahip."));
                        } else {
                            sender.sendMessage(new TextComponentString("Hata: " + errorMsg));
                        }
                    });
                }
                break;
            }
            case "url": {
                if (sender instanceof EntityPlayerMP) {
                    // Komutu çalıştıran oyuncunun ekranında onay penceresi aç.
                    SkinNetwork.NETWORK.sendTo(new OpenSkinConfirmMessage(targetName, value), (EntityPlayerMP) sender);
                    sender.sendMessage(new TextComponentString("Görseli onaylaman için bir pencere açıldı."));
                } else {
                    // Konsoldan (GUI'siz) çalıştırılıyorsa doğrudan uygula.
                    SkinUtils.copyFromUrl(server, target, value);
                    sender.sendMessage(new TextComponentString(targetName + " için yeni skin URL'den uygulandı: " + value));
                }
                break;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public java.util.List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "player", "url", "reset");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("player")) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return java.util.Collections.emptyList();
    }
}
