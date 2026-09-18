package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.piglin.PiglinBruteAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 猪灵蛮兵具有独立的受伤报复入口；满足附近有玩家等条件时阻止写入敌对报复逻辑，不取消伤害。
 */
@Mixin(PiglinBruteAi.class)
public abstract class PiglinBruteAiRetaliationMixin {
	@Inject(method = "wasHurtBy", at = @At("HEAD"), cancellable = true)
	private static void ultrahard$suppressRetaliation(
			ServerLevel level,
			PiglinBrute piglin,
			LivingEntity attacker,
			CallbackInfo ci
	) {
		// 玩家在附近时保留猪灵蛮兵的受伤效果，但跳过对敌对误伤者的仇恨记忆。
		if (UltraHardAi.shouldIgnoreHostileRetaliation(piglin, attacker)) {
			ci.cancel();
		}
	}
}
