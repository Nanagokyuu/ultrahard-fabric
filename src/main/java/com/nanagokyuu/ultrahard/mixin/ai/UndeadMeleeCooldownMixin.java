package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 编队换位会暂停原版近战任务，恢复时不能利用原版 start 清零来绕过攻击冷却。 */
@Mixin(MeleeAttackGoal.class)
public abstract class UndeadMeleeCooldownMixin {
	@Shadow @Final protected PathfinderMob mob;
	@Shadow private int ticksUntilNextAttack;
	@Unique private long ultrahard$attackReadyAt;

	@Unique
	private boolean ultrahard$usesUndeadTactics() {
		return (mob instanceof Zombie || mob instanceof WitherSkeleton)
				&& UltraHardDifficulties.isUltraHard(mob.level());
	}

	@Inject(method = "resetAttackCooldown", at = @At("TAIL"))
	private void ultrahard$rememberAttackCooldown(CallbackInfo ci) {
		if (ultrahard$usesUndeadTactics()) {
			// 保存绝对到期游戏刻，换位期间已经过去的时间仍计入原版冷却。
			ultrahard$attackReadyAt = mob.level().getGameTime() + ticksUntilNextAttack;
		}
	}

	@Inject(method = "start", at = @At("TAIL"))
	private void ultrahard$restoreAttackCooldown(CallbackInfo ci) {
		if (!ultrahard$usesUndeadTactics()) {
			ultrahard$attackReadyAt = 0L;
			return;
		}
		long remaining = Math.max(0L, ultrahard$attackReadyAt - mob.level().getGameTime());
		// 只补回尚未经过的冷却，不缩短当前值，也不在换回近战时重新计满一轮。
		ticksUntilNextAttack = Math.max(ticksUntilNextAttack, (int) Math.min(Integer.MAX_VALUE, remaining));
	}
}
