package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.entity.monster.zombie.Husk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 尸壳生成骆驼尸壳坐骑的概率提高50%，保留原版生成限制。 */
@Mixin(Husk.class)
public abstract class HuskJockeySpawnMixin {
	/** 只替换超困难的概率阈值，其它难度保留原值。 */
	@ModifyConstant(method = "finalizeSpawn", constant = @Constant(floatValue = 0.1f), require = 1, allow = 1)
	private float ultrahard$increaseJockeyOdds(float original) {
		Husk self = (Husk) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level()) ? 0.15f : original;
	}
}
