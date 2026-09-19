package com.nanagokyuu.ultrahard.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让守卫者攻击目标内部的难度判断识别超困难为困难，不替换原版目标选择或光束流程。
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Guardian$GuardianAttackGoal")
public abstract class GuardianAttackGoalHardMixin {
	@ModifyExpressionValue(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$asHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}
}
