package com.matter_moulder.lyumixdiscordauth.timer;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    private static boolean enabled;

    /** Thread-safe map of active login timers for players */
    private static final Map<String, Integer> loginTimers = Collections.synchronizedMap(new HashMap<>());

    public Timer() {
        reloadConfig();
    }

    /**
     * Updates timer display and checks for timeouts each server tick
     */
    public void onServerTick(MinecraftServer server) {
        if (!enabled) return;

        server.getPlayerManager().getPlayerList().forEach(player -> {
            String playerName = player.getName().getString();
            loginTimers.computeIfPresent(playerName, (key, timeRemaining) -> {
                if (timeRemaining <= 1) {
                    DenyHandle.kickPlayerTimedOut(player);
                    return null;
                }
                sendActionBarMessage(player, timeRemaining);
                return timeRemaining - 1;
            });
        });
    }

    public static void startLoginTimer(ServerPlayerEntity player) {
        loginTimers.put(player.getName().getString(), fullTime);
    }

    public static void stopLoginTimer(ServerPlayerEntity player) {
        loginTimers.remove(player.getName().getString());
    }

    public static void reloadConfig() {
        firstBarColor = Formatting.valueOf(ConfigMngr.conf().loginTimer.firstColor);
        secondBarColor = Formatting.valueOf(ConfigMngr.conf().loginTimer.secondColor);
        thirdBarColor = Formatting.valueOf(ConfigMngr.conf().loginTimer.thirdColor);
        thirdColorTime = ConfigMngr.conf().loginTimer.thirdTime * 20;
        fullTime = ConfigMngr.conf().loginTimer.loginTime * 20;
        secondColorTime = ConfigMngr.conf().loginTimer.secondTime * 20;
        title = ConfigMngr.conf().loginTimer.title;
        enabled = ConfigMngr.conf().loginTimer.enabled;
    }

    private void sendActionBarMessage(ServerPlayerEntity player, int timeRemaining) {
        Formatting color = timeRemaining > secondColorTime ? firstBarColor :
                           timeRemaining > thirdColorTime ? secondBarColor :
                                                            thirdBarColor;
                                                                        
        player.sendMessage(Text.literal(title + (timeRemaining / 20)).formatted(color), true);
    }
}
