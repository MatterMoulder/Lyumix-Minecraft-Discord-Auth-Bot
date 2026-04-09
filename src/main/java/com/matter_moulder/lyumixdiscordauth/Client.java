package com.matter_moulder.lyumixdiscordauth;

import com.matter_moulder.lyumixdiscordauth.auth.ModPackets;
import com.matter_moulder.lyumixdiscordauth.auth.client.AdminConfigScreen;
import com.matter_moulder.lyumixdiscordauth.auth.client.ClientRefreshTokenStore;
import com.matter_moulder.lyumixdiscordauth.auth.client.OAuthUiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) {
                return;
            }

            ButtonWidget button = ButtonWidget.builder(Text.translatable("menu.options"), btn ->
                            ClientPlayNetworking.send(ModPackets.ADMIN_CONFIG_OPEN_C2S_ID, PacketByteBufs.empty()))
                    .dimensions(screen.width - 72, 6, 66, 20)
                    .build();
            Screens.getButtons(screen).add(button);
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.PROBE_ID,
                (client, handler, buf, responseSender) -> {
                    String nonce;
                    try {
                        nonce = buf.readString(64);
                    } catch (Exception e) {
                        LOGGER.warn("Malformed PROBE packet from server: {}", Utils.sanitizeLog(e.getMessage()));
                        return;
                    }

                    String token = ClientRefreshTokenStore.loadToken();
                    ClientRefreshTokenStore.ProbeBindingData bindingData = ClientRefreshTokenStore.buildProbeBinding(nonce);
                    var response = PacketByteBufs.create();
                    response.writeString(token == null ? "" : token);
                    response.writeString(bindingData.bindingHash());
                    response.writeString(bindingData.machineIdHash());
                    response.writeString(bindingData.serverAddress());
                    ClientPlayNetworking.send(ModPackets.PROBE_RESPONSE_ID, response);
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.REFRESH_TOKEN_SYNC_ID,
                (client, handler, buf, responseSender) -> {
                    String token;
                    try {
                        token = buf.readString(1024);
                    } catch (Exception e) {
                        LOGGER.warn("Malformed packet from server: {}", Utils.sanitizeLog(e.getMessage()));
                        return;
                    }
                    client.execute(() -> {
                        ClientRefreshTokenStore.saveToken(token);
                        if (client.currentScreen instanceof OAuthUiScreen oauthUiScreen) {
                            oauthUiScreen.allowCloseAndClose();
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OAUTH_UI_ID,
                (client, handler, buf, responseSender) -> {
                    String authUrl;
                    try {
                        authUrl = buf.readString(4096);
                    } catch (Exception e) {
                        LOGGER.warn("Malformed packet from server: {}", Utils.sanitizeLog(e.getMessage()));
                        return;
                    }

                    client.execute(() -> {
                        if (client.player == null) {
                            return;
                        }
                        client.setScreen(new OAuthUiScreen(authUrl));
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.ADMIN_CONFIG_S2C_ID,
                (client, handler, buf, responseSender) -> {
                    NbtCompound data;
                    try {
                        data = buf.readNbt();
                    } catch (Exception e) {
                        LOGGER.warn("Malformed packet from server: {}", Utils.sanitizeLog(e.getMessage()));
                        return;
                    }
                    if (data == null) {
                        return;
                    }
                    client.execute(() -> client.setScreen(new AdminConfigScreen(data)));
                }
        );
    }
}
