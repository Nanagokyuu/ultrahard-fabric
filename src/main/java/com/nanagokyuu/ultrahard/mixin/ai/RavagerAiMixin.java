package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 劫掠兽覆写近战伤害方法，需独立取消正面砍盾并使用适合其体型的绕行半径。
 */
@Mixin(Ravager.class)
public abstract class RavagerAiMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void ultrahard$avoidShield(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		Ravager self = (Ravager) (Object) this;
		// 劫掠兽的攻击判定独立于普通 Mob，需要在专用入口拦截盾牌正面攻击。
		if (!UltraHardAi.shouldCancelShieldedMelee(self, target)) return;
		UltraHardAi.flankShield(self, (net.minecraft.world.entity.LivingEntity) target, 4.0, 1.1);
		cir.setReturnValue(false);
	}
}
