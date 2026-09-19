package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 溺尸生成僵尸鹦鹉螺坐骑的概率提高50%，保留原版生成限制。 */
@Mixin(Drowned.class)
public abstract class DrownedJockeySpawnMixin {
	/** 只替换超困难的概率阈值，其它难度保留原值。 */
	@ModifyConstant(method = "finalizeSpawn", constant = @Constant(floatValue = 0.5f), require = 1, allow = 1)
	private float ultrahard$increaseJockeyOdds(float original) {
		Drowned self = (Drowned) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level()) ? 0.75f : original;
	}
}
