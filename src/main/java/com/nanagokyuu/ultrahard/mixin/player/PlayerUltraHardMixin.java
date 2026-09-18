package com.nanagokyuu.ultrahard.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import com.nanagokyuu.ultrahard.UltraHardEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 统一放大玩家 exhaustion 输入，并在受伤流程中将超困难映射到困难分支；长期未进食倍率由事件模块计算。
 */
@Mixin(Player.class)
public abstract class PlayerUltraHardMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float ultrahard$fasterHunger(float exhaustion) {
		Player self = (Player) (Object) this;
		return exhaustion * UltraHardEvents.hungerExhaustionMultiplier(self);
	}

	@ModifyExpressionValue(
			method = "hurtServer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$asHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}
}
