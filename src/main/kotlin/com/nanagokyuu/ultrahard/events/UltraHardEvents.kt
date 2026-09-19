package com.nanagokyuu.ultrahard.events

import com.nanagokyuu.ultrahard.UltraHardAi
import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import com.nanagokyuu.ultrahard.equipment.UltraHardEquipment
import com.nanagokyuu.ultrahard.recovery.UltraHardRecovery
import kotlin.math.floor
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player

/**
 * 通过服务端事件协调伤害、吸血、当前装备和睡眠规则。
 * 每日睡眠治疗记录通过 UltraHardPlayerState 持久化；
 * 本次睡眠快照和待结算吸血仅保存在内存中，不作为玩家存档的一部分。
 */
object UltraHardEvents {
	/** 防止自定义伤害再次进入 ALLOW_DAMAGE，避免递归处理同一次攻击。 */
	private val bypassCustomDamage = ThreadLocal.withInitial { false }

	/** 初始化时注册一次；伤害回调记录结果，服务器帧末回调集中结算治疗并清理离线状态。 */
	fun register() {
		// 伤害事件负责处理三件互相独立的规则：敌人伤害倍率、玩家输出限伤、吸血记录。
		ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
			if (bypassCustomDamage.get()) {
				return@register true
			}

			val level = entity.level()
			if (level !is ServerLevel || !UltraHardDifficulties.isUltraHard(level)) {
				return@register true
			}

			val attacker = source.entity
			// 敌对生物之间允许保留原版互伤；是否产生仇恨由 Mob 的目标设置拦截统一处理。

			// Boss 保持原版伤害；每次受击都重新读取护甲槽，换装立即生效。
			if (entity is ServerPlayer && isHostile(attacker) && !isBoss(attacker)) {
				val multiplier = UltraHardEquipment.enemyDamageMultiplier(entity)
				if (multiplier == 1.0f) return@register true
				applyDamage(entity, level, source, amount * multiplier)
				return@register false
			}

			// 仅限制敌对生物。友好生物和玩家对战保留原版伤害。
			if (attacker is ServerPlayer && entity !== attacker && isHostile(entity)) {
				val cappedAmount = cappedPlayerDamage(amount, entity.maxHealth)
				val healthBefore = entity.health
				val absorptionBefore = entity.absorptionAmount
				if (applyDamage(entity, level, source, cappedAmount)) {
					val healthDamage = (healthBefore - entity.health).coerceAtLeast(0.0f)
					val absorptionDamage = (absorptionBefore - entity.absorptionAmount).coerceAtLeast(0.0f)
					// 近战和玩家投射物都按最终实际伤害积累仇恨；不改变吸血的触发规则。
					if (entity is Mob) UltraHardAi.recordPlayerDamage(entity, attacker, healthDamage + absorptionDamage)
					UltraHardLifesteal.queueLifesteal(attacker, level, source, healthDamage + absorptionDamage)
					if (cappedAmount < amount) {
						spawnDamageCapParticles(level, entity)
					}
				}
				return@register false
			}

			true
		}

		UltraHardKillStreak.register()

		ServerTickEvents.END_SERVER_TICK.register { server ->
			// 这些状态只存在于服务端内存中，玩家离线或难度切换时必须主动清理。
			val currentTick = server.overworld().gameTime
			for (player in server.playerList.players) {
				UltraHardKillStreak.trackGameMode(player)
				if (!UltraHardDifficulties.isUltraHard(player.level())) {
					// 离开超困难后立即清空战斗经验，防止切回超困难时恢复旧的战斗收益。
					UltraHardKillStreak.clear(player)
					// 切换离开 Ultra Hard 时，不能把旧难度的待结算吸血带回去。
					UltraHardLifesteal.clear(player)
					UltraHardSleep.clear(player)
					continue
				}
				initializeFoodClock(player, currentTick)
				UltraHardSleep.updateSleepState(player)
				UltraHardSleep.healSleepingPlayer(player)

				UltraHardLifesteal.tick(player, currentTick)
			}
			val onlinePlayerIds = server.playerList.players.map { it.uuid }.toSet()
			UltraHardLifesteal.clearOffline(onlinePlayerIds)
			UltraHardSleep.clearOffline(onlinePlayerIds)
			UltraHardKillStreak.clearOffline(onlinePlayerIds)
			server.allLevels.forEach { level -> UltraHardAi.tickTemporarySpiderWebs(level) }
		}
	}

	/** 按世界运行刻数计算未进食时长；严格超过配置天数才升级倍率，未初始化时使用基础倍率。 */
	@JvmStatic
	fun hungerExhaustionMultiplier(player: Player): Float {
		if (player !is ServerPlayer || !UltraHardDifficulties.isUltraHard(player.level())) return 1.0f
		val state = player as UltraHardPlayerState
		val currentTick = player.level().gameTime
		if (state.ultrahardGetLastFoodTick() == Long.MIN_VALUE) return UltraHardConfigs.values.hungerExhaustionMultiplier
		val starvingTicks = UltraHardConfigs.values.starvingAfterDays.toLong() * TICKS_PER_DAY
		return if (currentTick - state.ultrahardGetLastFoodTick() > starvingTicks) {
			UltraHardConfigs.values.starvingExhaustionMultiplier
		} else {
			UltraHardConfigs.values.hungerExhaustionMultiplier
		}
	}

	@JvmStatic
	fun recordEating(player: Player) {
		if (player is ServerPlayer && UltraHardDifficulties.isUltraHard(player.level())) {
			(player as UltraHardPlayerState).ultrahardSetLastFoodTick(player.level().gameTime)
		}
	}

	/** 重生仍保留当日休息限制，不能用死亡刷新治疗次数。 */
	@JvmStatic
	fun copyPlayerState(oldPlayer: ServerPlayer, newPlayer: ServerPlayer) {
		val oldState = oldPlayer as UltraHardPlayerState
		val newState = newPlayer as UltraHardPlayerState
		newState.ultrahardSetLastFoodTick(oldState.ultrahardGetLastFoodTick())
		newState.ultrahardSetLastSleepHealingDay(oldState.ultrahardGetLastSleepHealingDay())
		// 正常重生时死亡事件已经清零；非死亡替换则保留当前生命的战斗经验。
		newState.ultrahardSetLifeCombatExperience(if (oldPlayer.isAlive) oldState.ultrahardGetLifeCombatExperience() else 0)
		newState.ultrahardSetReceivedRulesBook(oldState.ultrahardHasReceivedRulesBook())
		newState.ultrahardSetSoupCooldownUntil(oldState.ultrahardGetSoupCooldownUntil())
		UltraHardRecovery.syncSoupCooldown(newPlayer)
	}

	private fun isHostile(entity: Entity?): Boolean {
		return entity is Enemy || entity is Monster
	}

	private fun isBoss(entity: Entity?): Boolean =
		entity is EnderDragon || entity is WitherBoss || entity is Warden

	/**
	 * 软上限基于目标最大生命值，而非当前剩余生命值。
	 * 阈值以内保持输入伤害；只有超出部分按比例折算，随后对合计结果向下取整。
	 */
	private fun cappedPlayerDamage(amount: Float, maxHealth: Float): Float {
		val cap = maxHealth * UltraHardConfigs.values.playerAttackCapFraction
		if (amount <= cap) return amount
		val overflow = (amount - cap) * UltraHardConfigs.values.playerAttackOverflowMultiplier
		return floor(cap + overflow)
	}

	/**
	 * 用调整后的数值重新进入原版伤害流程，仍由原版处理护甲、吸收生命等机制。
	 * 重入标记使内部伤害回调直接放行；外层回调再取消原始伤害，防止一次攻击扣血两次。
	 */
	private fun applyDamage(
		entity: LivingEntity,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
		amount: Float,
	): Boolean {
		if (!entity.isAlive) return false
		bypassCustomDamage.set(true)
		try {
			return entity.hurtServer(level, source, amount)
		} finally {
			bypassCustomDamage.remove()
		}
	}

	private fun initializeFoodClock(player: ServerPlayer, currentTick: Long) {
		val state = player as UltraHardPlayerState
		if (state.ultrahardGetLastFoodTick() == Long.MIN_VALUE) {
			state.ultrahardSetLastFoodTick(currentTick)
		}
	}

	private fun spawnDamageCapParticles(level: ServerLevel, entity: LivingEntity) {
		level.sendParticles(
			ParticleTypes.ENCHANTED_HIT,
			entity.x,
			entity.y + entity.bbHeight * 0.5,
			entity.z,
			UltraHardConfigs.values.damageCapParticleCount,
			0.25,
			0.25,
			0.25,
			0.0,
		)
	}

	private const val TICKS_PER_DAY = 24_000L
}
