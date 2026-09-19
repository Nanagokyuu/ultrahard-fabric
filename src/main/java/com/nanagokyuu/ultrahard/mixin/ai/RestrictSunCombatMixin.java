package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 近距离战斗时允许导航使用露天路径。 */
@Mixin(RestrictSunGoal.class)
public abstract class RestrictSunCombatMixin {
	@Shadow @Final private PathfinderMob mob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void ultrahard$allowSunlitPaths(CallbackInfoReturnable<Boolean> cir) {
		if (UltraHardAi.shouldPrioritizeNearbyPlayer(mob)) cir.setReturnValue(false);
	}
}
