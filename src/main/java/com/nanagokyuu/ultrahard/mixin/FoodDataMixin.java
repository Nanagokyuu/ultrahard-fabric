package com.nanagokyuu.ultrahard.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	/**
	 * Cancel natural (food/saturation) healing on Ultra Hard.
	 * Potions and other heal() callers are unaffected.
	 */
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"
			)
	)
	private void ultrahard$cancelNaturalRegen(ServerPlayer player, float amount) {
		if (UltraHardDifficulties.isUltraHard(player.level())) {
			return;
		}
		player.heal(amount);
	}

	/**
	 * Treat Ultra Hard like Hard for starvation / food tick difficulty branches.
	 */
	@ModifyExpressionValue(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$hardCompatible(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}
}
