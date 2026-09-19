package com.nanagokyuu.ultrahard.mixin.ai;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 将蜘蛛骑士判定从百分之一提高到百分之三，不改变其它生成随机数。 */
@Mixin(Spider.class)
public abstract class SpiderJockeySpawnMixin {
	/** 原版仅在结果为零时生成骑手；将零、一、二统一映射为成功。 */
	@ModifyExpressionValue(
			method = "finalizeSpawn",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 0)
	)
	private int ultrahard$increaseJockeyOdds(int original) {
		Spider self = (Spider) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level()) && original < 3 ? 0 : original;
	}
}
