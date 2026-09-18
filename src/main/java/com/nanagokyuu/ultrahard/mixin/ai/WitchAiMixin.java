package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女巫更新时处理后排站位及友军治疗；远程攻击入口仅在自定义近身投药成功时取消原版投掷。
 */
@Mixin(Witch.class)
public abstract class WitchAiMixin {
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void ultrahard$holdBackAndHealRaiders(CallbackInfo ci) {
		UltraHardAi.tickWitch((Witch) (Object) this);
	}

	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void ultrahard$sequenceCloseRangePotions(LivingEntity target, float pullProgress, CallbackInfo ci) {
		// 仅拦截近身玩家目标；治疗袭击单位和远距离攻击仍走原版逻辑。
		if (UltraHardAi.performCloseRangeAttack((Witch) (Object) this, target)) {
			ci.cancel();
		}
	}
}
