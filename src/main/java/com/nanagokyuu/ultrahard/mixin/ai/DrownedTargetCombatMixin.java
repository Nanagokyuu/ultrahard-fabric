package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 允许溺尸在白天攻击附近岸上的玩家。 */
@Mixin(Drowned.class)
public abstract class DrownedTargetCombatMixin {
	@Inject(method = "okTarget", at = @At("HEAD"), cancellable = true)
	private void ultrahard$attackNearbyLandPlayer(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		Drowned self = (Drowned) (Object) this;
		if (UltraHardDifficulties.isUltraHard(self.level()) && UltraHardAi.isNearbyPlayer(self, target)) {
			cir.setReturnValue(true);
		}
	}
}
