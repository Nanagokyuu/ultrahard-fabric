package com.nanagokyuu.ultrahard.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在闪电相关难度判断处复用困难分支，不修改世界持久化保存的实际难度值。
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltHardMixin {
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
