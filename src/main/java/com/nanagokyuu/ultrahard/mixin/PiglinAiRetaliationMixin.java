package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PiglinAi.class)
public abstract class PiglinAiRetaliationMixin {
	@Inject(method = "wasHurtBy", at = @At("HEAD"), cancellable = true)
	private static void ultrahard$suppressRetaliation(
			ServerLevel level,
			Piglin piglin,
			LivingEntity attacker,
			CallbackInfo ci
	) {
		// 玩家在附近时允许猪灵受到伤害，但不让误伤者触发猪灵的群体仇恨。
		if (UltraHardAi.shouldIgnoreHostileRetaliation(piglin, attacker)) {
			ci.cancel();
		}
	}
}
