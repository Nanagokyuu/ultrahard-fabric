package com.nanagokyuu.ultrahard.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonHardMixin {
	/** 让骷髅继续使用困难难度下的武器重评估规则。 */
	@ModifyExpressionValue(
			method = "reassessWeaponGoal",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$asHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}

	@org.spongepowered.asm.mixin.injection.Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void ultrahard$flankShieldBeforeShooting(LivingEntity target, float pullProgress, CallbackInfo ci) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		// 出箭时再次核对朝向，防止玩家在骷髅蓄力期间转身举盾后仍被正面射击。
		if (UltraHardAi.shouldFlankShield(self, target)) {
			UltraHardAi.flankShield(self, target);
			ci.cancel();
		}
	}

	@ModifyArg(
			method = "performRangedAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
			),
			index = 7
	)
	private float ultrahard$improveArrowAccuracy(float original) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		// 替换原版发射调用的最后一个参数，即箭矢散布值；具体数值仍由配置控制。
		return UltraHardDifficulties.isUltraHard(self.level())
				? com.nanagokyuu.ultrahard.UltraHardConfigs.getValues().getSkeletonArrowInaccuracy()
				: original;
	}
}
