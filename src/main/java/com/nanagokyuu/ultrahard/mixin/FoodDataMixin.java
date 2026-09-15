package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	/**
	 * Cancel natural (food/saturation) healing while Ultra Hard is enabled,
	 * similar to naturalRegeneration=false. Potions and other heal() callers are unaffected.
	 */
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"
			)
	)
	private void ultrahard$cancelNaturalRegen(ServerPlayer player, float amount) {
		if (UltraHardGameRules.isEnabled(player.level())) {
			return;
		}
		player.heal(amount);
	}
}
