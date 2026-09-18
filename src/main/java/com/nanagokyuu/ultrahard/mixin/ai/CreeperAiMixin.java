package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 分别在刻开始、爆炸入口和刻结束处理绕盾、取消不合适的爆炸与疏散，避免仅在刻末处理而错过引爆时机。
 */
@Mixin(Creeper.class)
public abstract class CreeperAiMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void ultrahard$flankShieldedPlayer(CallbackInfo ci) {
		UltraHardAi.tickCreeperShield((Creeper) (Object) this);
	}

	@Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
	private void ultrahard$cancelShieldedExplosion(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		if (UltraHardAi.shouldFlankCreeperShield(self)) {
			UltraHardAi.tickCreeperShield(self);
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$evacuateNearbyMonsters(CallbackInfo ci) {
		// 放在苦力怕自身状态更新后，才能准确判断是否已经进入引爆阶段。
		UltraHardAi.tickCreeper((Creeper) (Object) this);
	}
}
