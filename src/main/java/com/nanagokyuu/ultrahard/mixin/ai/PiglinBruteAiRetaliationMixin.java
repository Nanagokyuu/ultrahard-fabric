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
