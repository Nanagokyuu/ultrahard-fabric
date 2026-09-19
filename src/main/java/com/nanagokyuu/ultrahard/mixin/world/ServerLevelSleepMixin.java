package com.nanagokyuu.ultrahard.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import com.nanagokyuu.ultrahard.UltraHardEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在原版深睡眠人数条件检查时结算超困难睡眠治疗；跳夜资格完全沿用原版。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSleepMixin {
	/**
	 * 在原版跳夜分支入口统一检查，避免禁止跳夜后仍被强制唤醒或清除天气。
	 * 原版会在人数和深睡眠条件满足后跳夜；这里额外加入 Ultra Hard 的血量条件。
	 * 治疗必须在原版推进时间之前结算，否则跳到下一天后会使用错误的日期。
	 */
	@ModifyExpressionValue(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"
			)
	)
	private boolean ultrahard$checkRestBeforeSkippingNight(boolean original) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (original && UltraHardDifficulties.isUltraHard(level)) {
			level.players().stream().filter(ServerPlayer::isSleeping).forEach(UltraHardEvents::healSleepingPlayer);
		}
		return original;
	}
}
