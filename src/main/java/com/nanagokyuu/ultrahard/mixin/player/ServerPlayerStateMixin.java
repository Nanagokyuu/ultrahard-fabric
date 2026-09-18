package com.nanagokyuu.ultrahard.mixin.player;

import com.nanagokyuu.ultrahard.UltraHardPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为服务端玩家注入持久状态；在原版读写存档完成后处理自定义键，缺失键使用初始值以兼容旧存档。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerStateMixin implements UltraHardPlayerState {
	/** 禁止跳夜日期持久化，防止通过重登或重生绕过重伤休息限制。 */
	@Unique private long ultrahard$sleepBlockedDay = Long.MIN_VALUE;
	@Unique private long ultrahard$lastFoodTick = Long.MIN_VALUE;
	@Unique private long ultrahard$lastSleepHealingDay = Long.MIN_VALUE;
	@Unique private boolean ultrahard$receivedRulesBook;

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void ultrahard$readState(ValueInput input, CallbackInfo ci) {
		// 旧护甲里程碑不再读取，装备倍率始终按当前穿戴情况实时计算。
		ultrahard$sleepBlockedDay = input.getLongOr("UltraHardSleepBlockedCalendarDay", Long.MIN_VALUE);
		ultrahard$lastFoodTick = input.getLongOr("UltraHardLastFoodTick", Long.MIN_VALUE);
		// 旧值使用 gameTime，不能与新的昼夜日期混用。
		ultrahard$lastSleepHealingDay = input.getLongOr("UltraHardLastSleepHealingCalendarDay", Long.MIN_VALUE);
		ultrahard$receivedRulesBook = input.getBooleanOr("UltraHardReceivedRulesBook", false);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void ultrahard$writeState(ValueOutput output, CallbackInfo ci) {
		output.putLong("UltraHardSleepBlockedCalendarDay", ultrahard$sleepBlockedDay);
		output.putLong("UltraHardLastFoodTick", ultrahard$lastFoodTick);
		output.putLong("UltraHardLastSleepHealingCalendarDay", ultrahard$lastSleepHealingDay);
		output.putBoolean("UltraHardReceivedRulesBook", ultrahard$receivedRulesBook);
	}

	@Override public long ultrahardGetSleepBlockedDay() { return ultrahard$sleepBlockedDay; }
	@Override public void ultrahardSetSleepBlockedDay(long value) { ultrahard$sleepBlockedDay = value; }
	@Override public long ultrahardGetLastFoodTick() { return ultrahard$lastFoodTick; }
	@Override public void ultrahardSetLastFoodTick(long value) { ultrahard$lastFoodTick = value; }
	@Override public long ultrahardGetLastSleepHealingDay() { return ultrahard$lastSleepHealingDay; }
	@Override public void ultrahardSetLastSleepHealingDay(long value) { ultrahard$lastSleepHealingDay = value; }
	@Override public boolean ultrahardHasReceivedRulesBook() { return ultrahard$receivedRulesBook; }
	@Override public void ultrahardSetReceivedRulesBook(boolean value) { ultrahard$receivedRulesBook = value; }
}
