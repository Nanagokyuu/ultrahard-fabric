package com.nanagokyuu.ultrahard.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.animal.bee.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 仅在蜜蜂造成伤害时兼容困难难度判断，保留原版攻击流程，不新增主动攻击目标。
 */
@Mixin(Bee.class)
public abstract class BeeHardMixin {
	@ModifyExpressionValue(
			method = "doHurtTarget",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$asHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}
}
