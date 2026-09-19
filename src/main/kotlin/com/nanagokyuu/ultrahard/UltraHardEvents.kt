package com.nanagokyuu.ultrahard

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.core.registries.Registries
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
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.GameType
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import kotlin.math.floor
import java.util.UUID

/**
 * 通过服务端事件协调伤害、吸血、当前装备和睡眠规则。
 * 每日睡眠治疗记录通过 UltraHardPlayerState 持久化；
 * 本次睡眠快照和待结算吸血仅保存在内存中，不作为玩家存档的一部分。
 */
object UltraHardEvents {
	/** 防止自定义伤害再次进入 ALLOW_DAMAGE，避免递归处理同一次攻击。 */
	private val bypassCustomDamage = ThreadLocal.withInitial { false }
	/** 记录每名玩家的待结算吸血和上次成功触发时间。 */
	private val lifestealStates = HashMap<UUID, LifestealState>()
	/** 记录玩家本次入睡时的血量，用于区分是否允许跳夜。 */
	private val sleepStates = HashMap<UUID, SleepState>()
	/** 记录上一帧的游戏模式，用于检测进入创造或旁观并清空当前生命击杀数。 */
	private val previousGameModes = HashMap<UUID, GameType>()

	private data class LifestealState(
		/** 同一 tick 内多次命中只保留最高一次回血量。 */
		var pendingHealing: Float = 0.0f,
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
					queueLifesteal(attacker, level, source, healthDamage + absorptionDamage)
					if (cappedAmount < amount) {
						spawnDamageCapParticles(level, entity)
					}
				}
				return@register false
			}

			true
		}

		// 生物死亡后再统计击杀，确保只有真正死亡的敌对生物才会增加连击数。
		ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
			if (entity is ServerPlayer) {
				val wasUltraHard = UltraHardDifficulties.isUltraHard(entity.level())
				val kills = lifeKills(entity)
				val correction = (1.0f - UltraHardEquipment.killDamageMultiplier(entity)) * 100.0f
				resetLifeKills(
					entity,
					if (wasUltraHard) "本次生命结束：击杀 ${kills} 个敌对生物，敌人伤害修正 -${formatPercent(correction)}%，已清零。" else null,
				)
				return@register
			}
			if (entity.level() !is ServerLevel || !UltraHardDifficulties.isUltraHard(entity.level())) return@register
			if (!isHostile(entity)) return@register
			val killer = resolvePlayerKiller(source) ?: return@register
			if (killer.isCreative || killer.isSpectator) return@register
			addLifeKill(killer)
		}

		ServerTickEvents.END_SERVER_TICK.register { server ->
			// 这些状态只存在于服务端内存中，玩家离线或难度切换时必须主动清理。
			val currentTick = server.overworld().gameTime
			for (player in server.playerList.players) {
				trackGameMode(player)
				if (!UltraHardDifficulties.isUltraHard(player.level())) {
					// 离开超困难后立即清空连击，防止切回超困难时恢复旧的战斗收益。
					if (lifeKills(player) > 0) resetLifeKills(player)
					// 切换离开 Ultra Hard 时，不能把旧难度的待结算吸血带回去。
					lifestealStates.remove(player.uuid)
					sleepStates.remove(player.uuid)
					continue
				}
				initializeFoodClock(player, currentTick)
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
				player.heal(healing)
				state.pendingHealing = 0.0f
				state.lastTriggerTick = currentTick
			}
			lifestealStates.keys.removeIf { uuid -> server.playerList.getPlayer(uuid) == null }
			sleepStates.keys.removeIf { uuid -> server.playerList.getPlayer(uuid) == null }
			previousGameModes.keys.removeIf { uuid -> server.playerList.getPlayer(uuid) == null }
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

	/** 重生仍保留当日休息限制，不能用死亡刷新治疗次数或跳夜资格。 */
	@JvmStatic
	fun copyPlayerState(oldPlayer: ServerPlayer, newPlayer: ServerPlayer) {
		val oldState = oldPlayer as UltraHardPlayerState
		val newState = newPlayer as UltraHardPlayerState
		newState.ultrahardSetLastFoodTick(oldState.ultrahardGetLastFoodTick())
		newState.ultrahardSetLastSleepHealingDay(oldState.ultrahardGetLastSleepHealingDay())
		// 正常重生时死亡事件已经清零；非死亡替换则保留当前生命的击杀数。
		newState.ultrahardSetLifeKills(if (oldPlayer.isAlive) oldState.ultrahardGetLifeKills() else 0)
		newState.ultrahardSetReceivedRulesBook(oldState.ultrahardHasReceivedRulesBook())
	}

	/** 返回玩家当前生命累计的有效击杀数。 */
	private fun lifeKills(player: ServerPlayer): Int =
		(player as UltraHardPlayerState).ultrahardGetLifeKills()

	/** 增加击杀数，并在每达到一个十击杀节点时显示正反馈。 */
	private fun addLifeKill(player: ServerPlayer) {
		val state = player as UltraHardPlayerState
		val oldKills = state.ultrahardGetLifeKills()
		val newKills = oldKills + 1
		state.ultrahardSetLifeKills(newKills)
		val interval = UltraHardConfigs.values.killDamageMilestoneInterval
		if (newKills / interval <= oldKills / interval) return

		val multiplier = UltraHardEquipment.killDamageMultiplier(player)
		val correction = (1.0f - multiplier) * 100.0f
		// 粒子和 Action Bar 都只在十击杀节点触发，不污染聊天框。
		player.level().sendParticles(
			ParticleTypes.HAPPY_VILLAGER,
			player.x,
			player.y + player.bbHeight * 0.5,
			player.z,
			12,
			0.35,
			0.5,
			0.35,
			0.05,
		)
		player.sendOverlayMessage(
			Component.literal("战斗连击：${newKills}｜敌人伤害修正：-${formatPercent(correction)}%"),
		)
	}

	/** 清空当前生命击杀数，并在死亡时保留一条总结提示。 */
	private fun resetLifeKills(player: ServerPlayer, message: String? = null) {
		val state = player as UltraHardPlayerState
		state.ultrahardSetLifeKills(0)
		if (message != null) player.sendSystemMessage(Component.literal(message))
	}

	/** 只允许玩家本人或玩家发射的投射物获得击杀计数。 */
	private fun resolvePlayerKiller(source: net.minecraft.world.damagesource.DamageSource): ServerPlayer? {
		(source.entity as? ServerPlayer)?.let { return it }
		val projectile = source.directEntity as? Projectile ?: return null
		return projectile.owner as? ServerPlayer
	}

	/** 进入创造或旁观时立即清空连击，避免通过指令保留战斗收益。 */
	private fun trackGameMode(player: ServerPlayer) {
		val mode = player.gameMode()
		val previous = previousGameModes.put(player.uuid, mode)
		if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
			if (previous != mode || lifeKills(player) > 0) resetLifeKills(player)
		}
	}

	private fun formatPercent(value: Float): String = "%.0f".format(value)

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
		// 按实际伤害保留小数治疗，不取整，也不设置最低一血保底。
		val healing = damage * healingRatio
		val state = lifestealStates.getOrPut(player.uuid) { LifestealState() }
		val currentTick = level.gameTime
		// 冷却期间的攻击不进入 pendingHealing，冷却结束后也不会补算。
		if (state.lastTriggerTick != Long.MIN_VALUE
			&& currentTick - state.lastTriggerTick < UltraHardConfigs.values.lifestealCooldownTicks) return
		state.pendingHealing = maxOf(state.pendingHealing, healing)
	}

	private fun initializeFoodClock(player: ServerPlayer, currentTick: Long) {
		val state = player as UltraHardPlayerState
		if (state.ultrahardGetLastFoodTick() == Long.MIN_VALUE) {
			state.ultrahardSetLastFoodTick(currentTick)
		}
	}

	/** 只在首次检测到睡眠时记录血量和日期；离床便丢弃快照，再次入睡重新建立。 */
	private fun updateSleepState(player: ServerPlayer) {
		if (!player.isSleeping) {
			sleepStates.remove(player.uuid)
			return
		}
		// 跨日仍躺在床上时也刷新快照，避免旧日期影响新一天的治疗。
		val day = Math.floorDiv(player.level().overworldClockTime, TICKS_PER_DAY)
		if (sleepStates[player.uuid]?.calendarDay == day) return
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
		val config = UltraHardConfigs.values
		val healing = (player.maxHealth * config.sleepHealingFraction)
			.coerceIn(config.sleepHealingMinimum, config.sleepHealingMaximum)
		player.heal(healing)
		if (!sleepState.healingReported) {
			sleepState.healingReported = true
			player.sendOverlayMessage(Component.literal("睡眠治疗完成：恢复 %.1f 点生命值。".format(healing)))
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
	private const val SLEEP_HEALING_DELAY_TICKS = 100
}
