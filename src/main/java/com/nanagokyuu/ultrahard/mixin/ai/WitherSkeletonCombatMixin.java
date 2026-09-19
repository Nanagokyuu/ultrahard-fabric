package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 超困难生成时随机选择弓手；近战换位与远程战术在 Kotlin 中按当前武器分流。 */
@Mixin(WitherSkeleton.class)
public abstract class WitherSkeletonCombatMixin {
	/**
	 * 原版先装备石剑，再进行一次独立的 10% 抽签，命中才替换主手为弓。
	 * 此入口属于生成装备流程，读档、普通 tick 和切换难度不会重新抽签。
	 * 后续原版 finalizeSpawn 会重评估武器目标，因此直接复用原版弓箭任务。
	 */
	@Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
	private void ultrahard$spawnWithBow(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
		WitherSkeleton self = (WitherSkeleton) (Object) this;
		if (self.level().isClientSide() || !UltraHardDifficulties.isUltraHard(self.level())) return;
		if (random.nextFloat() < 0.10F) {
			self.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
	}

	/** 保留原版凋灵效果；即使本次攻击被格挡，近战兵种仍可安排下一次换位。 */
	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void ultrahard$changeAttackDirection(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		UltraHardAi.afterWitherSkeletonAttack((WitherSkeleton) (Object) this, target);
	}
}
