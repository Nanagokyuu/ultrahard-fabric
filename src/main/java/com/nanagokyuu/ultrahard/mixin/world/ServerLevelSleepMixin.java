package com.nanagokyuu.ultrahard.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import com.nanagokyuu.ultrahard.UltraHardEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在原版深睡眠人数条件之后追加超困难跳夜资格检查，并在时钟推进之前结算睡眠治疗。
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
		if (!original || !UltraHardDifficulties.isUltraHard(level)) return original;

		var sleepers = level.players().stream().filter(ServerPlayer::isSleeping).toList();
		// 原版会先跳到次日再唤醒玩家，因此必须在这里按睡眠当日结算治疗。
		sleepers.forEach(UltraHardEvents::healSleepingPlayer);
		// 保留原版睡眠人数比例，同时保证所有实际参与者都连续睡满五秒。
		return !sleepers.isEmpty() && sleepers.stream().allMatch(UltraHardEvents::canSkipNight);
	}
}
