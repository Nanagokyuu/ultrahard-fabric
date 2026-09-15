package com.nanagokyuu.ultrahard.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerUltraHardMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float ultrahard$fasterHunger(float exhaustion) {
		Player self = (Player) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level())
				? exhaustion * UltraHardDifficulties.HUNGER_EXHAUSTION_MULTIPLIER
				: exhaustion;
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
