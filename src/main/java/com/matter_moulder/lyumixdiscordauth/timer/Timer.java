package com.matter_moulder.lyumixdiscordauth.timer;

import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages login timeout functionality.
 * Displays countdown timer and handles player kicks on timeout.
 */
public class Timer {
    private static Formatting firstBarColor;
    private static Formatting secondBarColor;
    private static Formatting thirdBarColor;
    private static int thirdColorTime;
    private static int fullTime;
    private static int secondColorTime;
    private static String title;
    private static boolean isEnabled;

    /** Thread-safe map of active login timers for players */
    private static final Map<UUID, Integer> LOGIN_TIMERS = Collections.synchronizedMap(new HashMap<>());

    public Timer() {
        reloadConfig();
    }

    /**
     * Updates timer display and checks for timeouts each server tick
     */
    public void onServerTick(MinecraftServer server) {
        if (!isEnabled) {
            return;
        }

        server.getPlayerManager().getPlayerList().forEach(player -> {
            UUID playerUuid = player.getUuid();
            LOGIN_TIMERS.computeIfPresent(playerUuid, (key, timeRemaining) -> {
                if (timeRemaining <= 1) {
                    PlayerAuthManager.kickPlayerTimedOut(player);
                    return null;
                }
                sendActionBarMessage(player, timeRemaining);
                return timeRemaining - 1;
            });
        });
    }

    public static void startLoginTimer(ServerPlayerEntity player) {
        LOGIN_TIMERS.put(player.getUuid(), fullTime);
    }

    public static void stopLoginTimer(ServerPlayerEntity player) {
        LOGIN_TIMERS.remove(player.getUuid());
    }

    public static void reloadConfig() {
        firstBarColor = Formatting.valueOf(ConfigManager.conf().loginTimer.firstColor);
        secondBarColor = Formatting.valueOf(ConfigManager.conf().loginTimer.secondColor);
        thirdBarColor = Formatting.valueOf(ConfigManager.conf().loginTimer.thirdColor);
        thirdColorTime = ConfigManager.conf().loginTimer.thirdTime * 20;
        fullTime = ConfigManager.conf().loginTimer.loginTime * 20;
        secondColorTime = ConfigManager.conf().loginTimer.secondTime * 20;
        title = ConfigManager.conf().loginTimer.title;
        isEnabled = ConfigManager.conf().loginTimer.enabled;
    }

    private void sendActionBarMessage(ServerPlayerEntity player, int timeRemaining) {
        Formatting color = timeRemaining > secondColorTime ? firstBarColor :
                           timeRemaining > thirdColorTime ? secondBarColor :
                                                            thirdBarColor;

        player.sendMessage(Text.literal(title + (timeRemaining / 20)).formatted(color), true);
    }
}
