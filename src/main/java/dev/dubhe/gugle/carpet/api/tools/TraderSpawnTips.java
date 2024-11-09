package dev.dubhe.gugle.carpet.api.tools;

import dev.dubhe.gugle.carpet.GcaSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class TraderSpawnTips {
    public static void fail(MinecraftServer server, String tips) {
        if (GcaSetting.traderSpawnTips) {
            Component component = Component.literal("[GCA] Trader spawn failed, reason: %s".formatted(tips)).withStyle(ChatFormatting.GOLD);
            server.getPlayerList().broadcastSystemMessage(component, false);
        }
    }
}
