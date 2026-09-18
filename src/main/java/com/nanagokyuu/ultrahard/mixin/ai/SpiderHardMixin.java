package com.nanagokyuu.ultrahard.mixin.ai;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Spider.class)
public abstract class SpiderHardMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$ambushShieldedPlayer(CallbackInfo ci) {
		// 蜘蛛在自身 tick 末尾更新偷袭路线，避免被原版寻路立即覆盖。
		UltraHardAi.tickSpider((Spider) (Object) this);
	}

	@ModifyExpressionValue(
			method = "finalizeSpawn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/ServerLevelAccessor;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$asHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}
}
