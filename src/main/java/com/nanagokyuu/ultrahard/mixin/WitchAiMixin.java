package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
