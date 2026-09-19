package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 幼体僵尸的两条鸡骑士分支各提高为三倍，保留年龄与附近鸡的原版条件。 */
@Mixin(Zombie.class)
public abstract class ZombieJockeySpawnMixin {
	/** 只替换超困难的概率阈值，其它难度保留原值。 */
	@ModifyConstant(method = "finalizeSpawn", constant = @Constant(doubleValue = 0.05d), require = 2, allow = 2)
	private double ultrahard$increaseJockeyOdds(double original) {
		Zombie self = (Zombie) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level()) ? 0.15d : original;
	}
}
