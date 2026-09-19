package com.nanagokyuu.ultrahard.mixin.ai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 将新生成僵尸的随机幼体概率从5%提高到10%，其它难度保持原版。 */
@Mixin(Zombie.class)
public abstract class ZombieBabySpawnMixin {
	@WrapOperation(
			method = "finalizeSpawn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;getSpawnAsBabyOdds(Lnet/minecraft/util/RandomSource;)Z"
			)
	)
	private boolean ultrahard$increaseBabyOdds(RandomSource random, Operation<Boolean> original) {
		Zombie self = (Zombie) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level()) ? random.nextFloat() < 0.1f : original.call(random);
	}
}
