package com.nanagokyuu.ultrahard.mixin.difficulty;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让 Ultra Hard 使用困难模式的区域难度分支，同时保留 id=4 对应的难度缩放。
 */
@Mixin(DifficultyInstance.class)
public abstract class DifficultyInstanceMixin {
	@ModifyExpressionValue(
			method = "calculateDifficulty",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/world/Difficulty;HARD:Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$matchHardOrUltra(
			Difficulty hard,
			Difficulty difficulty,
			long timeElapsed,
			long chunkInhabitedTime,
			float moonPhaseFactor
	) {
		return UltraHardDifficulties.isUltraHard(difficulty) ? difficulty : hard;
	}
}
