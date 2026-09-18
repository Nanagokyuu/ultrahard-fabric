package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在掠夺者 AI 更新中检查盾牌正面，必要时停止当前使用物品动作并请求侧后方站位。
 */
@Mixin(Pillager.class)
public abstract class PillagerAiMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void ultrahard$avoidShield(LivingEntity target, float pullProgress, CallbackInfo ci) {
		Pillager self = (Pillager) (Object) this;
		// 弩箭已经装填完成但玩家正面举盾时，先取消射击并换到侧后方。
		if (!UltraHardAi.shouldFlankShield(self, target)) return;
		self.stopUsingItem();
		UltraHardAi.flankShield(self, target, 1.15);
		ci.cancel();
	}
}
