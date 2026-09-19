package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 附近有可攻击玩家时暂停原版逃离阳光任务。 */
@Mixin(FleeSunGoal.class)
public abstract class FleeSunCombatMixin {
	@Shadow @Final protected PathfinderMob mob;

	@Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
	private void ultrahard$prioritizeNearbyPlayer(CallbackInfoReturnable<Boolean> cir) {
		if (UltraHardAi.shouldPrioritizeNearbyPlayer(mob)) cir.setReturnValue(false);
	}
}
