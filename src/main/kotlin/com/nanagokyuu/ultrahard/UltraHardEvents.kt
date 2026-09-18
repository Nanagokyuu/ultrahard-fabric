package com.nanagokyuu.ultrahard

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import kotlin.math.floor
import java.util.UUID

object UltraHardEvents {
	/** 防止自定义伤害再次进入 ALLOW_DAMAGE，避免递归处理同一次攻击。 */
	private val bypassCustomDamage = ThreadLocal.withInitial { false }
	/** 记录每名玩家的待结算吸血和上次成功触发时间。 */
	private val lifestealStates = HashMap<UUID, LifestealState>()
	/** 记录玩家本次入睡时的血量，用于区分是否允许跳夜。 */
	private val sleepStates = HashMap<UUID, SleepState>()

	private data class LifestealState(
		/** 同一 tick 内多次命中只保留最高一次回血量。 */
		var pendingHealing: Int = 0,
		/** 上次实际治疗发生的服务器 tick，而不是上次攻击发生的 tick。 */
		var lastTriggerTick: Long = Long.MIN_VALUE,
	)

	private data class SleepState(
		/** 必须使用入睡瞬间的血量，不能使用五秒后的治疗后血量。 */
		val healthWhenSleepStarted: Float,
		/** 以主世界昼夜日期作为每日治疗的唯一标识。 */
		val calendarDay: Long,
		var healingReported: Boolean = false,
	)

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

			// Boss 保持原版伤害；其他敌对生物的伤害按受击玩家的装备进度提升。
			if (entity is ServerPlayer && isHostile(attacker) && !isBoss(attacker)) {
				val multiplier = enemyDamageMultiplier(entity)
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
					queueLifesteal(attacker, level, source, healthDamage + absorptionDamage)
					if (cappedAmount < amount) {
						spawnDamageCapParticles(level, entity)
					}
				}
				return@register false
			}

			true
		}

		ServerTickEvents.END_SERVER_TICK.register { server ->
			// 这些状态只存在于服务端内存中，玩家离线或难度切换时必须主动清理。
			val currentTick = server.overworld().gameTime
			for (player in server.playerList.players) {
				if (!UltraHardDifficulties.isUltraHard(player.level())) {
					// 切换离开 Ultra Hard 时，不能把旧难度的待结算吸血带回去。
					lifestealStates.remove(player.uuid)
					sleepStates.remove(player.uuid)
					continue
				}
				updatePlayerProgress(player, currentTick)
				updateSleepState(player)
				healSleepingPlayer(player)

				val state = lifestealStates[player.uuid]
				if (state == null) continue
				if (!player.isAlive) {
					// 死亡时清除未结算的治疗，避免复活后凭空获得上一条攻击的回血。
					lifestealStates.remove(player.uuid)
					continue
				}
				if (state.pendingHealing <= 0) continue
				if (state.lastTriggerTick != Long.MIN_VALUE && currentTick - state.lastTriggerTick < UltraHardConfigs.values.lifestealCooldownTicks) continue

				val healing = state.pendingHealing
				player.heal(healing.toFloat())
				state.pendingHealing = 0
				state.lastTriggerTick = currentTick
			}
			lifestealStates.keys.removeIf { uuid -> server.playerList.getPlayer(uuid) == null }
			sleepStates.keys.removeIf { uuid -> server.playerList.getPlayer(uuid) == null }
			server.allLevels.forEach { level -> UltraHardAi.tickTemporarySpiderWebs(level) }
		}
	}

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

	@JvmStatic
	fun copyPlayerState(oldPlayer: ServerPlayer, newPlayer: ServerPlayer) {
		val oldState = oldPlayer as UltraHardPlayerState
		val newState = newPlayer as UltraHardPlayerState
		newState.ultrahardSetIronMilestone(oldState.ultrahardHasIronMilestone())
		newState.ultrahardSetDiamondMilestone(oldState.ultrahardHasDiamondMilestone())
		newState.ultrahardSetLastFoodTick(oldState.ultrahardGetLastFoodTick())
		newState.ultrahardSetLastSleepHealingDay(oldState.ultrahardGetLastSleepHealingDay())
		newState.ultrahardSetReceivedRulesBook(oldState.ultrahardHasReceivedRulesBook())
	}

	private fun isHostile(entity: Entity?): Boolean {
		return entity is Enemy || entity is Monster
	}

	private fun isBoss(entity: Entity?): Boolean =
		entity is EnderDragon || entity is WitherBoss || entity is Warden

	private fun enemyDamageMultiplier(player: ServerPlayer): Float {
		val state = player as UltraHardPlayerState
		// 钻石阶段独立于铁阶段，玩家只要获得过任意钻石盔甲即可直接进入最高倍率。
		return when {
			state.ultrahardHasDiamondMilestone() -> UltraHardConfigs.values.enemyDamageAfterDiamondMultiplier
			state.ultrahardHasIronMilestone() -> UltraHardConfigs.values.enemyDamageAfterIronMultiplier
			else -> UltraHardConfigs.values.enemyDamageBaseMultiplier
		}
	}

	private fun cappedPlayerDamage(amount: Float, maxHealth: Float): Float {
		val cap = maxHealth * UltraHardConfigs.values.playerAttackCapFraction
		if (amount <= cap) return amount
		val overflow = (amount - cap) * UltraHardConfigs.values.playerAttackOverflowMultiplier
		return floor(cap + overflow)
	}

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

	private fun queueLifesteal(
		player: ServerPlayer,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
		damage: Float,
	) {
		if (damage <= 0.0f) return
		// 只有玩家本人的近战直接命中才允许吸血；远程、投射物和其他实体伤害一律排除。
		if (source.directEntity !== player || player.getAttackStrengthScale(0.5f) < 1.0f) return

		val enchantment = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(UltraHardEnchantments.LIFESTEAL)
		val levelValue = EnchantmentHelper.getItemEnchantmentLevel(enchantment, player.mainHandItem)
		if (levelValue <= 0) return

		val healingRatio = when {
			levelValue >= UltraHardConfigs.values.lifestealMaximumRatioLevel -> UltraHardConfigs.values.lifestealMaximumRatio
			else -> UltraHardConfigs.values.lifestealBaseRatio * (levelValue + 1)
		}
		val healing = floor(damage * healingRatio).toInt()
			.coerceAtLeast(UltraHardConfigs.values.lifestealMinimumHealing)
		val state = lifestealStates.getOrPut(player.uuid) { LifestealState() }
		val currentTick = level.gameTime
		// 冷却期间的攻击不进入 pendingHealing，冷却结束后也不会补算。
		if (state.lastTriggerTick != Long.MIN_VALUE
			&& currentTick - state.lastTriggerTick < UltraHardConfigs.values.lifestealCooldownTicks) return
		state.pendingHealing = maxOf(state.pendingHealing, healing)
	}

	private fun updatePlayerProgress(player: ServerPlayer, currentTick: Long) {
		if (!UltraHardDifficulties.isUltraHard(player.level())) return
		val state = player as UltraHardPlayerState
		val inventory = player.inventory
		// 只比较物品类型，附魔、已损耗以及穿在身上的护甲也算获得过。
		if (!state.ultrahardHasIronMilestone() && inventory.contains { stack ->
			!stack.isEmpty && (stack.`is`(Items.IRON_HELMET) || stack.`is`(Items.IRON_CHESTPLATE)
				|| stack.`is`(Items.IRON_LEGGINGS) || stack.`is`(Items.IRON_BOOTS))
		}) {
			state.ultrahardSetIronMilestone(true)
			player.sendOverlayMessage(Component.literal("已获得铁盔甲：敌对生物伤害提高至 ${UltraHardConfigs.values.enemyDamageAfterIronMultiplier} 倍。"))
		}
		if (!state.ultrahardHasDiamondMilestone() && inventory.contains { stack ->
			!stack.isEmpty && (stack.`is`(Items.DIAMOND_HELMET) || stack.`is`(Items.DIAMOND_CHESTPLATE)
				|| stack.`is`(Items.DIAMOND_LEGGINGS) || stack.`is`(Items.DIAMOND_BOOTS))
		}) {
			state.ultrahardSetDiamondMilestone(true)
			player.sendOverlayMessage(Component.literal("已获得钻石盔甲：敌对生物伤害提高至 ${UltraHardConfigs.values.enemyDamageAfterDiamondMultiplier} 倍。"))
		}
		if (state.ultrahardGetLastFoodTick() == Long.MIN_VALUE) {
			state.ultrahardSetLastFoodTick(currentTick)
		}
	}

	private fun updateSleepState(player: ServerPlayer) {
		if (!player.isSleeping) {
			sleepStates.remove(player.uuid)
			return
		}
		if (sleepStates.containsKey(player.uuid)) return
		// 只在检测到“刚开始睡眠”的第一个服务端 tick 建立快照。
		val day = Math.floorDiv(player.level().overworldClockTime, TICKS_PER_DAY)
		sleepStates[player.uuid] = SleepState(player.health, day)
		player.sendOverlayMessage(Component.literal("已入睡，连续睡满 5 秒后结算今日睡眠治疗。"))
	}

	@JvmStatic
	fun healSleepingPlayer(player: ServerPlayer) {
		if (!UltraHardDifficulties.isUltraHard(player.level()) || !player.isAlive
			|| !player.isSleeping || player.sleepTimer < SLEEP_HEALING_DELAY_TICKS) return
		val sleepState = sleepStates[player.uuid] ?: return
		val state = player as UltraHardPlayerState
		// 使用昼夜时钟而非服务器运行时长，睡觉跳到次日后治疗次数也随之刷新。
		val day = sleepState.calendarDay
		if (state.ultrahardGetLastSleepHealingDay() == day) return

		// 在跳夜之前结算并记录旧日期，避免起床后漏治疗或占用次日的次数。
		state.ultrahardSetLastSleepHealingDay(day)
		// 入睡前达到阈值时回满，否则只给固定治疗，并由 canSkipNight 拒绝跳夜。
		val healing = if (sleepState.healthWhenSleepStarted >= SLEEP_SKIP_HEALTH_THRESHOLD) {
			player.maxHealth - player.health
		} else {
			UltraHardConfigs.values.sleepHealingAmount
		}
		player.heal(healing)
		if (!sleepState.healingReported) {
			sleepState.healingReported = true
			player.sendOverlayMessage(
				Component.literal(if (sleepState.healthWhenSleepStarted >= SLEEP_SKIP_HEALTH_THRESHOLD)
					"睡眠治疗完成：生命值已回满，可以跳过夜晚。"
				else "睡眠治疗完成：恢复 ${UltraHardConfigs.values.sleepHealingAmount} 点；入睡前生命值不足 10，不能跳过夜晚。"),
			)
		}
	}

	@JvmStatic
	fun canSkipNight(player: ServerPlayer): Boolean =
		player.isAlive && player.isSleeping && player.sleepTimer >= SLEEP_HEALING_DELAY_TICKS
			&& (sleepStates[player.uuid]?.healthWhenSleepStarted ?: 0.0f) >= SLEEP_SKIP_HEALTH_THRESHOLD

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
	private const val SLEEP_HEALING_DELAY_TICKS = 100
	private const val SLEEP_SKIP_HEALTH_THRESHOLD = 10.0f
}
