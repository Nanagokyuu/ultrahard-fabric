package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 近距离战斗期间暂停溺尸回水目标，避免与追击争抢移动。 */
@Mixin(targets = "net.minecraft.world.entity.monster.zombie.Drowned$DrownedGoToWaterGoal")
public abstract class DrownedWaterCombatMixin {
	@Shadow @Final private PathfinderMob mob;

	@Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
	private void ultrahard$leaveWaterForCombat(CallbackInfoReturnable<Boolean> cir) {
		if (UltraHardAi.shouldPrioritizeNearbyPlayer(mob)) cir.setReturnValue(false);
	}
}
