package com.nanagokyuu.ultrahard.mixin.ai;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CaveSpider.class)
public abstract class CaveSpiderHardMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void ultrahard$avoidShield(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		CaveSpider self = (CaveSpider) (Object) this;
		// 洞穴蜘蛛覆盖了父类攻击方法，因此需要在这里单独阻止正面砍盾。
		if (!UltraHardAi.shouldCancelShieldedMelee(self, target)) return;
		UltraHardAi.flankShield(self, (net.minecraft.world.entity.LivingEntity) target, 3.0, 1.25);
		cir.setReturnValue(false);
	}

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
