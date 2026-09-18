package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截末影人独立的目标设置入口以抑制敌对误伤报复，并在 AI 步骤末尝试主动盲区瞬移。
 */
@Mixin(EnderMan.class)
public abstract class EndermanAiMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void ultrahard$ignoreHostileRetaliation(LivingEntity target, CallbackInfo ci) {
		EnderMan self = (EnderMan) (Object) this;
		// 末影人覆写了目标设置方法，因此单独阻止它因敌对误伤锁定攻击者。
		if (target != null && UltraHardAi.shouldIgnoreHostileRetaliation(self, target)) {
			ci.cancel();
		}
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void ultrahard$ambushFromBlindSpot(CallbackInfo ci) {
		// 末影人在 AI 步骤末尾寻找玩家视线外的瞬移落点，保留原版受伤瞬移逻辑。
		UltraHardAi.tickEnderman((EnderMan) (Object) this);
	}

}
