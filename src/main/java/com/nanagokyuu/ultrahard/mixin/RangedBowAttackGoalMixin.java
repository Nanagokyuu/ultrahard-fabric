package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalMixin {
	/** 原版弓箭 AI 会继续横移射击，因此需要在盾牌目标出现时暂时接管 tick。 */
	@Shadow @Final private Monster mob;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void ultrahard$prioritizeShieldFlanking(CallbackInfo ci) {
		if (!(mob instanceof AbstractSkeleton skeleton)) return;
		var target = skeleton.getTarget();
		if (target == null || !UltraHardAi.shouldFlankShield(skeleton, target)) return;

		// 暂停原版横移和蓄力逻辑，否则原版目标点会每 tick 覆盖绕背路径。
		skeleton.stopUsingItem();
		skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
		UltraHardAi.flankShield(skeleton, target);
		ci.cancel();
	}
}
