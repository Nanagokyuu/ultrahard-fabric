package com.nanagokyuu.ultrahard.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieHardMixin {
	@ModifyExpressionValue(
			method = {"hurtServer", "killedEntity"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$serverLevelAsHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}

	@ModifyExpressionValue(
			method = "populateDefaultEquipmentSlots",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$levelAsHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}

	@Inject(method = "lambda$static$0", at = @At("HEAD"), cancellable = true)
	private static void ultrahard$doorBreaking(Difficulty difficulty, CallbackInfoReturnable<Boolean> cir) {
		if (UltraHardDifficulties.isUltraHard(difficulty)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$coordinateZombieAttack(CallbackInfo ci) {
		UltraHardAi.tickZombie((Zombie) (Object) this);
	}
}
