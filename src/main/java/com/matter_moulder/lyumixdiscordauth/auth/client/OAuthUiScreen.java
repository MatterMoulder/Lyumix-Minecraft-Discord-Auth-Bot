package com.matter_moulder.lyumixdiscordauth.auth.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OAuthUiScreen extends Screen {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final String authUrl;
    private final String qrUrl;
    private Identifier qrTextureId;
    private boolean loadingQr = true;
    private String qrError = null;
    private boolean allowClose;

    public OAuthUiScreen(String authUrl) {
        super(Text.literal("Discord Authorization"));
        this.authUrl = authUrl;
        this.qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=320x320&data="
                + URLEncoder.encode(authUrl, StandardCharsets.UTF_8);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int bottomY = this.height - 56;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Authorize"), button -> Util.getOperatingSystem().open(this.authUrl))
                .dimensions(centerX - 155, bottomY, 150, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Open QR in Browser"), button -> Util.getOperatingSystem().open(this.qrUrl))
                .dimensions(centerX + 5, bottomY, 150, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("menu.disconnect"), button -> disconnectClient())
                .dimensions(centerX - 75, bottomY + 24, 150, 20)
                .build());

        loadQrTexture();
    }

    private void disconnectClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        this.allowClose = true;
        if (client.world != null) {
            client.world.disconnect();
        }
        client.disconnect();
        client.setScreen(new TitleScreen());
    }

    public void allowCloseAndClose() {
        this.allowClose = true;
        this.close();
    }

    private void loadQrTexture() {
        this.loadingQr = true;
        this.qrError = null;

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(qrUrl))
                                .GET()
                                .build();
                        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                        if (response.statusCode() != 200) {
                            throw new IllegalStateException("QR HTTP " + response.statusCode());
                        }
                        try (InputStream stream = response.body()) {
                            return NativeImage.read(stream);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((image, throwable) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> {
                        this.loadingQr = false;
                        if (throwable != null || image == null) {
                            this.qrError = "Failed to load QR";
                            return;
                        }

                        if (this.qrTextureId != null) {
                            client.getTextureManager().destroyTexture(this.qrTextureId);
                        }

                        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                        this.qrTextureId = client.getTextureManager().registerDynamicTexture(
                                "lda_oauth_qr_" + UUID.randomUUID(),
                                texture
                        );
                    });
                });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Discord Login"), centerX, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Scan QR with your phone or click Authorize"), centerX, 34, 0xAFAFAF);

        int qrSize = 220;
        int qrX = centerX - (qrSize / 2);
        int qrY = 54;

        if (this.qrTextureId != null) {
            context.drawTexture(this.qrTextureId, qrX, qrY, 0.0F, 0.0F, qrSize, qrSize, qrSize, qrSize);
        } else if (this.loadingQr) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading QR..."), centerX, qrY + (qrSize / 2), 0xFFFF55);
        } else {
            String error = this.qrError == null ? "QR unavailable" : this.qrError;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(error), centerX, qrY + (qrSize / 2), 0xFF5555);
        }
    }

    @Override
    public void close() {
        if (!allowClose) {
            return;
        }
        if (this.qrTextureId != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(this.qrTextureId);
            this.qrTextureId = null;
        }
        super.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}

