package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerStateMixin implements UltraHardPlayerState {
	/** 以下字段会写入玩家存档，用于让装备阶段和规则书状态跨重登保留。 */
	@Unique private boolean ultrahard$ironMilestone;
	@Unique private boolean ultrahard$diamondMilestone;
	@Unique private long ultrahard$lastFoodTick = Long.MIN_VALUE;
	@Unique private long ultrahard$lastSleepHealingDay = Long.MIN_VALUE;
	@Unique private boolean ultrahard$receivedRulesBook;

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void ultrahard$readState(ValueInput input, CallbackInfo ci) {
		// 新键不继承旧材料进度，避免曾捡过铁锭/钻石的旧存档直接触发护甲阶段。
		ultrahard$ironMilestone = input.getBooleanOr("UltraHardIronArmorMilestone", false);
		ultrahard$diamondMilestone = input.getBooleanOr("UltraHardDiamondArmorMilestone", false);
		ultrahard$lastFoodTick = input.getLongOr("UltraHardLastFoodTick", Long.MIN_VALUE);
		// 旧值使用 gameTime，不能与新的昼夜日期混用。
		ultrahard$lastSleepHealingDay = input.getLongOr("UltraHardLastSleepHealingCalendarDay", Long.MIN_VALUE);
		ultrahard$receivedRulesBook = input.getBooleanOr("UltraHardReceivedRulesBook", false);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void ultrahard$writeState(ValueOutput output, CallbackInfo ci) {
		output.putBoolean("UltraHardIronArmorMilestone", ultrahard$ironMilestone);
		output.putBoolean("UltraHardDiamondArmorMilestone", ultrahard$diamondMilestone);
		output.putLong("UltraHardLastFoodTick", ultrahard$lastFoodTick);
		output.putLong("UltraHardLastSleepHealingCalendarDay", ultrahard$lastSleepHealingDay);
		output.putBoolean("UltraHardReceivedRulesBook", ultrahard$receivedRulesBook);
	}

	@Override public boolean ultrahardHasIronMilestone() { return ultrahard$ironMilestone; }
	@Override public void ultrahardSetIronMilestone(boolean value) { ultrahard$ironMilestone = value; }
	@Override public boolean ultrahardHasDiamondMilestone() { return ultrahard$diamondMilestone; }
	@Override public void ultrahardSetDiamondMilestone(boolean value) { ultrahard$diamondMilestone = value; }
	@Override public long ultrahardGetLastFoodTick() { return ultrahard$lastFoodTick; }
	@Override public void ultrahardSetLastFoodTick(long value) { ultrahard$lastFoodTick = value; }
	@Override public long ultrahardGetLastSleepHealingDay() { return ultrahard$lastSleepHealingDay; }
	@Override public void ultrahardSetLastSleepHealingDay(long value) { ultrahard$lastSleepHealingDay = value; }
	@Override public boolean ultrahardHasReceivedRulesBook() { return ultrahard$receivedRulesBook; }
	@Override public void ultrahardSetReceivedRulesBook(boolean value) { ultrahard$receivedRulesBook = value; }
}
