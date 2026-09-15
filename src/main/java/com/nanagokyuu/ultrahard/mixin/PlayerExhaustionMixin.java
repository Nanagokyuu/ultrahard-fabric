package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float ultrahard$fasterHunger(float exhaustion) {
		Player self = (Player) (Object) this;
		if (UltraHardDifficulties.isUltraHard(self.level())) {
			return exhaustion * 1.5f;
		}
		return exhaustion;
	}
}
