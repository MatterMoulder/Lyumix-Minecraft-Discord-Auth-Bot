package com.matter_moulder.lyumixdiscordauth.auth;

import com.matter_moulder.lyumixdiscordauth.Server;
import net.minecraft.util.Identifier;

public class ModPackets {
    public static final Identifier PROBE_ID = new Identifier(Server.MOD_ID, "probe");
    public static final Identifier PROBE_RESPONSE_ID = new Identifier(Server.MOD_ID, "probe-response");
    public static final Identifier REFRESH_TOKEN_SYNC_ID = new Identifier(Server.MOD_ID, "refresh-token-sync");
    public static final Identifier OAUTH_UI_ID = new Identifier(Server.MOD_ID, "oauth-ui");
    public static final Identifier ADMIN_CONFIG_OPEN_C2S_ID = new Identifier(Server.MOD_ID, "admin-config-open");
    public static final Identifier ADMIN_CONFIG_S2C_ID = new Identifier(Server.MOD_ID, "admin-config-sync");
    public static final Identifier ADMIN_CONFIG_SAVE_C2S_ID = new Identifier(Server.MOD_ID, "admin-config-save");
}
