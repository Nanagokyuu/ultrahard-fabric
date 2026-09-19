package com.nanagokyuu.ultrahard.mixin.ai;

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

/**
 * 骷髅进入侧翼、后方或群体包围站位时暂停原版弓箭目标，避免横移覆盖编队导航。
 */
@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalMixin {
	/** 原版弓箭 AI 会继续横移射击，因此需要在编队移动期间暂时接管 tick。 */
	@Shadow @Final private Monster mob;
	@Shadow private boolean strafingBackwards;

	/** 原版横移会把不可走台阶改为前进，因此在移动控制前保留后退意图并尝试越障。 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$jumpBackwardOverStep(CallbackInfo ci) {
		if (strafingBackwards && mob instanceof AbstractSkeleton skeleton) {
			UltraHardAi.trySkeletonBackwardJump(skeleton);
		}
	}

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
