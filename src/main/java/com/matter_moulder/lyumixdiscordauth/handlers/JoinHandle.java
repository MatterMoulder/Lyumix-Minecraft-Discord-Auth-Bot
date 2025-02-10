package com.matter_moulder.lyumixdiscordauth.handlers;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class JoinHandle {
    private static final long MILLIS_PER_HOUR = 60 * 60 * 1000;
    public static void onPlayerJoin(ServerPlayerEntity player) {
        String playersIp = player.getIp();
        DatabaseManager db = Main.getDatabase();

        if (!DenyHandle.checkPlayer(player)) {
            DenyHandle.blockPlayer(player);
        }
        Object playerId = db.getPlayerIdByName(player.getName().getString());

        if (playerId != null) {
            Long lastLoginTime = db.getPlayerLastLoginTime(playerId);

            if (db.getPlayerIp(playerId).equals(playersIp.toString()) && lastLoginTime != null) {
                Long currentTime = System.currentTimeMillis();
                long diffTS = currentTime - lastLoginTime;
                if (diffTS <= ConfigMngr.conf().login.autoLoginTime * MILLIS_PER_HOUR) {
                    DenyHandle.unblockPlayer(player);
                    return;
                }
            }
            String loginMessage = ConfigMngr.msg().auth.loginRequired + "\n";
            Text message = Text.literal(loginMessage).formatted(Formatting.YELLOW);

            player.sendMessage(message, false);
            Main.getDsBot().sendConfirm(playerId, player.getIp());
        }
    }
}
