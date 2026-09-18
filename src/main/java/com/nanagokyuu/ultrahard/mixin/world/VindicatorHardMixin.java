package com.nanagokyuu.ultrahard.mixin.world;

import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.illager.Vindicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Vindicator.class)
public abstract class VindicatorHardMixin {
	@Inject(method = "lambda$static$0", at = @At("HEAD"), cancellable = true)
	private static void ultrahard$doorBreaking(Difficulty difficulty, CallbackInfoReturnable<Boolean> cir) {
		if (UltraHardDifficulties.isUltraHard(difficulty)) {
			cir.setReturnValue(true);
		}
	}
}
