package com.example.examplemod.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;

/**
 * "/copyskin <oyuncu> url <link>" komutundan sonra açılan onay penceresi.
 * Verilen URL'deki görseli indirip önizleme olarak gösterir; "Tamam"a basılırsa
 * skin gerçekten uygulanır (ConfirmSkinMessage sunucuya gönderilir).
 */
@SideOnly(Side.CLIENT)
public class GuiSkinConfirm extends GuiScreen {

    private static final int PREVIEW_BOX_SIZE = 128;

    private final String targetPlayerName;
    private final String skinUrl;

    private ResourceLocation previewTexture;
    private int imageWidth = 64;
    private int imageHeight = 64;
    private boolean loading = true;
    private boolean errored = false;

    public GuiSkinConfirm(String targetPlayerName, String skinUrl) {
        this.targetPlayerName = targetPlayerName;
        this.skinUrl = skinUrl;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int centerX = this.width / 2;
        int bottomY = this.height / 2 + 100;

        this.buttonList.add(new GuiButton(0, centerX - 105, bottomY, 100, 20, "Tamam"));
        this.buttonList.add(new GuiButton(1, centerX + 5, bottomY, 100, 20, "İptal"));

        loadPreviewImage();
    }

    /** Görseli arka planda (ayrı thread) indirir, sonra client thread'inde texture'a dönüştürür. */
    private void loadPreviewImage() {
        loading = true;
        errored = false;
        final String urlSnapshot = skinUrl;

        new Thread(() -> {
            try {
                BufferedImage image = ImageIO.read(new URL(urlSnapshot));
                if (image == null) {
                    throw new IllegalStateException("Görsel okunamadı (desteklenmeyen format olabilir)");
                }

                Minecraft.getMinecraft().addScheduledTask(() -> {
                    imageWidth = image.getWidth();
                    imageHeight = image.getHeight();
                    DynamicTexture dynamicTexture = new DynamicTexture(image);
                    previewTexture = Minecraft.getMinecraft().getTextureManager()
                            .getDynamicTextureLocation("copyskin_preview_" + System.nanoTime(), dynamicTexture);
                    loading = false;
                });
            } catch (Exception e) {
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    loading = false;
                    errored = true;
                });
            }
        }, "CopySkin-PreviewLoader").start();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int panelWidth = 220;
        int panelHeight = 220;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2 - 20;

        // Minecraft'ın vanilla dialoglarına benzer açık gri/beyazımsı panel
        drawRect(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF8B8B8B);
        drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFC6C6C6);

        int boxX = centerX - PREVIEW_BOX_SIZE / 2;
        int boxY = panelY + 15;
        drawRect(boxX - 1, boxY - 1, boxX + PREVIEW_BOX_SIZE + 1, boxY + PREVIEW_BOX_SIZE + 1, 0xFF373737);

        if (loading) {
            drawCenteredString(this.fontRenderer, "Yükleniyor...", centerX, boxY + PREVIEW_BOX_SIZE / 2 - 4, 0x404040);
        } else if (errored || previewTexture == null) {
            drawCenteredString(this.fontRenderer, "Görsel yüklenemedi", centerX, boxY + PREVIEW_BOX_SIZE / 2 - 4, 0xCC0000);
        } else {
            GlStateManager.color(1F, 1F, 1F, 1F);
            this.mc.getTextureManager().bindTexture(previewTexture);
            drawScaledCustomSizeModalRect(boxX, boxY, 0, 0, imageWidth, imageHeight,
                    PREVIEW_BOX_SIZE, PREVIEW_BOX_SIZE, imageWidth, imageHeight);
        }

        drawCenteredString(this.fontRenderer, "Yüklediğiniz Görsel Bu mu?", centerX, boxY + PREVIEW_BOX_SIZE + 12, 0x202020);
        drawCenteredString(this.fontRenderer, "Hedef oyuncu: " + targetPlayerName, centerX, boxY + PREVIEW_BOX_SIZE + 24, 0x505050);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            SkinNetwork.NETWORK.sendToServer(new ConfirmSkinMessage(targetPlayerName, skinUrl));
            if (this.mc.player != null) {
                this.mc.player.sendMessage(new TextComponentString("Skin uygulanıyor..."));
            }
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
