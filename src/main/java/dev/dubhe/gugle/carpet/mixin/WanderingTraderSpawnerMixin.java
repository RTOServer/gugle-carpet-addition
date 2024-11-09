package dev.dubhe.gugle.carpet.mixin;

import dev.dubhe.gugle.carpet.api.tools.TraderSpawnTips;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(WanderingTraderSpawner.class)
public class WanderingTraderSpawnerMixin {
    @Unique
    private double gca$i;

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(III)I"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void saveChance(ServerLevel serverLevel, boolean bl, boolean bl2, CallbackInfoReturnable<Integer> cir, int i) {
        this.gca$i = i / 10.0;
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "RETURN",
            ordinal = 4
        )
    )
    private void chance(@NotNull ServerLevel serverLevel, boolean bl, boolean bl2, CallbackInfoReturnable<Integer> cir) {
        TraderSpawnTips.fail(serverLevel.getServer(), "随机数不满足生成概率，当前概率：%s%%".formatted(this.gca$i));
    }

    @Inject(
        method = "spawn",
        at = @At(
            value = "RETURN",
            ordinal = 1
        )
    )
    private void chance2(@NotNull ServerLevel serverLevel, CallbackInfoReturnable<Boolean> cir) {
        TraderSpawnTips.fail(serverLevel.getServer(), "随机数不满足生成概率，当前概率：%s%%".formatted(this.gca$i));
    }

    @Inject(
        method = "spawn",
        at = @At(
            value = "RETURN",
            ordinal = 2
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void biome(@NotNull ServerLevel serverLevel, CallbackInfoReturnable<Boolean> cir, @NotNull Player player) {
        TraderSpawnTips.fail(serverLevel.getServer(), "群系不满足生成条件，当前玩家：%s%%".formatted(player.getGameProfile().getName()));
    }

    @Inject(
        method = "spawn",
        at = @At(
            value = "RETURN",
            ordinal = 4
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void pos(@NotNull ServerLevel serverLevel, CallbackInfoReturnable<Boolean> cir, @NotNull Player player) {
        TraderSpawnTips.fail(serverLevel.getServer(), "位置不满足生成条件或没有足够空间，当前玩家：%s%%".formatted(player.getGameProfile().getName()));
    }
}
