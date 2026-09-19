package com.nanagokyuu.ultrahard.ai

import com.nanagokyuu.ultrahard.ai.combat.CombatAi
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.monster.illager.Pillager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
import net.minecraft.world.entity.raid.Raider
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions

/**
 * 管理苦力怕引爆前的绕盾与疏散，以及女巫、掠夺者、唤魔者的站位和施法行为。
 * 药水弹道保留原版发射方式；只在满足近身玩家条件时接管女巫药水选择。
 */
internal object RangedAi {
	private data class CreeperFlankState(val targetId: java.util.UUID, var failedAttempts: Int = 0)
	private val creeperFlankStates = java.util.WeakHashMap<Creeper, CreeperFlankState>()

	@JvmStatic
	fun tickCreeperShield(creeper: Creeper) {
		AiSupport.ultraHardLevel(creeper) ?: return
		val target = creeper.target as? Player
		if (target == null || !target.isAlive || !target.isBlocking) {
			creeperFlankStates.remove(creeper)
			return
		}
		// 引爆计时被暂停时保留尝试状态，等待原版下一次重新进入引爆阶段。
		if (creeper.swellDir <= 0) return
		if (!isFacingShield(creeper, target)) {
			creeperFlankStates.remove(creeper)
			return
		}
		val state = creeperFlankStates[creeper]?.takeIf { it.targetId == target.uuid }
			?: CreeperFlankState(target.uuid).also { creeperFlankStates[creeper] = it }
		// 第二次失败后不再取消引爆，直接让原版苦力怕爆炸。
		if (state.failedAttempts >= MAX_FLANK_FAILURES) return

		if (CombatAi.shouldFlankShield(creeper, target)) {
			// 只有真正进入绕盾流程时才暂停引爆；失败次数由下方的失败状态检查增加。
			creeper.swellDir = -1
			CombatAi.flankShield(creeper, target, UltraHardConfigs.values.creeperEvacuationSpeed)
			recordCreeperFlankFailure(creeper, target, state)
			return
		}

		recordCreeperFlankFailure(creeper, target, state)
	}

	/** 将一次路径不可达或绕行超时记为失败；第二次失败后保留原版引爆状态。 */
	private fun recordCreeperFlankFailure(
		creeper: Creeper,
		target: Player,
		state: CreeperFlankState,
	) {
		if (!CombatAi.flankAttemptFailed(creeper, target)) return
		state.failedAttempts++
		if (state.failedAttempts < MAX_FLANK_FAILURES) {
			// 第一次失败后清掉通用状态，下一次引爆阶段重新寻找绕后路线。
			CombatAi.resetFlankState(creeper)
			creeper.swellDir = -1
		}
	}

	@JvmStatic
	fun shouldFlankCreeperShield(creeper: Creeper): Boolean {
		AiSupport.ultraHardLevel(creeper) ?: return false
		val target = creeper.target as? Player ?: return false
		if (!target.isAlive || !target.isBlocking || creeper.swellDir <= 0) return false
		if (!isFacingShield(creeper, target)) return false
		return creeperFlankStates[creeper]?.let { it.targetId == target.uuid && it.failedAttempts < MAX_FLANK_FAILURES } != false
	}

	private fun isFacingShield(creeper: Creeper, target: Player): Boolean {
		val toTarget = AiSupport.horizontalDirection(target.position().subtract(creeper.position()))
		// 只有苦力怕确实朝向玩家时，举盾才会让它改变战术。
		return AiSupport.horizontalDirection(creeper.lookAngle).dot(toTarget) >= 0.25
	}

	@JvmStatic
	fun tickCreeper(creeper: Creeper) {
		val level = AiSupport.ultraHardLevel(creeper) ?: return
		if (creeper.swellDir <= 0 || !AiSupport.shouldUpdate(creeper)) return
		val config = UltraHardConfigs.values
		// 仅在苦力怕已经开始引爆后才打断其他敌对生物的原有寻路。
		for (entity in level.getEntities(creeper, creeper.boundingBox.inflate(config.creeperEvacuationRadius)) { it !== creeper }) {
			val mob = entity as? Mob ?: continue
			if (!AiSupport.isHostile(mob) || mob is Creeper) continue
			val direction = AiSupport.horizontalDirection(mob.position().subtract(creeper.position()))
			AiSupport.moveTo(mob, creeper.position().add(direction.scale(config.creeperEvacuationDistance)), config.creeperEvacuationSpeed)
		}
	}

	@JvmStatic
	fun tickWitch(witch: Witch) {
		val level = AiSupport.ultraHardLevel(witch) ?: return
		if (!AiSupport.shouldUpdate(witch)) return
		val config = UltraHardConfigs.values
		// 受伤比例最低的袭击单位优先获得治疗，覆盖原本对玩家的进攻目标。
		val woundedRaider = level.getEntities(witch, witch.boundingBox.inflate(config.witchHealRadius)) { entity ->
			entity is Raider && entity !== witch && entity.isAlive && AiSupport.isWitchHealingPriority(entity) && entity.health < entity.maxHealth
		}.filterIsInstance<Raider>().minByOrNull { it.health / it.maxHealth }
		if (woundedRaider != null) {
			witch.target = woundedRaider
			return
		}

		val player = witch.target as? ServerPlayer ?: return
		if (CombatAi.maintainFrontalPressure(witch, player)) return
		if (CombatAi.shouldFlankShield(witch, player)) {
			witch.stopUsingItem()
			CombatAi.flankShield(witch, player, config.witchRetreatSpeed)
			return
		}
		if (player.distanceToSqr(witch) < config.witchBacklineDistance * config.witchBacklineDistance) {
			val away = AiSupport.horizontalDirection(witch.position().subtract(player.position()))
			AiSupport.moveTo(witch, witch.position().add(away.scale(config.witchBacklineDistance)), config.witchRetreatSpeed)
		}
	}

	fun tickPillager(pillager: Pillager) {
		if (!AiSupport.shouldUpdate(pillager)) return
		val target = pillager.target ?: return
		if (CombatAi.shouldFlankShield(pillager, target)) {
			pillager.stopUsingItem()
			CombatAi.flankShield(pillager, target, 1.15)
		} else if (CombatAi.maintainFrontalPressure(pillager, target)) {
			return
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(pillager) < 36.0) {
			AiSupport.ambushApproach(pillager, target, 8.0, 1.15)
		}
	}

	fun tickEvoker(evoker: Evoker) {
		if (!AiSupport.shouldUpdate(evoker)) return
		val target = evoker.target ?: return
		if (CombatAi.shouldFlankShield(evoker, target)) {
			CombatAi.flankShield(evoker, target, 1.1)
		} else if (CombatAi.maintainFrontalPressure(evoker, target)) {
			return
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(evoker) < 64.0) {
			AiSupport.ambushApproach(evoker, target, 7.0, 1.1)
		}
	}

	@JvmStatic
	fun nextWitchPotion(witch: Witch, target: LivingEntity, original: Holder<Potion>): Holder<Potion> {
		if (AiSupport.ultraHardLevel(witch) == null || target !is ServerPlayer) return original
		val maxDistance = UltraHardConfigs.values.witchBacklineDistance
		if (target.distanceToSqr(witch) > maxDistance * maxDistance) return original

		// 近身遭遇固定先削弱、再减速，之后持续使用伤害药水。
		val step = AiSupport.witchPotionSteps.getOrDefault(witch.uuid, 0)
		AiSupport.witchPotionSteps[witch.uuid] = step + 1
		return when (step) {
			0 -> Potions.WEAKNESS
			1 -> Potions.SLOWNESS
			else -> Potions.HARMING
		}
	}

	@JvmStatic
	fun performCloseRangeAttack(witch: Witch, target: LivingEntity): Boolean {
		val level = AiSupport.ultraHardLevel(witch) ?: return false
		if (witch.isDrinkingPotion() || target !is ServerPlayer) return false
		val maxDistance = UltraHardConfigs.values.witchBacklineDistance
		if (target.distanceToSqr(witch) > maxDistance * maxDistance) return false

		// 复用原版女巫的弹道预判和抛射参数，只替换近身时的药水选择。
		val targetVelocity = target.deltaMovement
		val deltaX = target.x + targetVelocity.x - witch.x
		val deltaY = target.eyeY - 1.1 - witch.y
		val deltaZ = target.z + targetVelocity.z - witch.z
		val horizontalDistance = kotlin.math.sqrt(deltaX * deltaX + deltaZ * deltaZ)
		val potion = nextWitchPotion(witch, target, Potions.HARMING)
		val potionStack = PotionContents.createItemStack(Items.SPLASH_POTION, potion)
		Projectile.spawnProjectileUsingShoot(
			::ThrownSplashPotion,
			level,
			potionStack,
			witch,
			deltaX,
			deltaY + horizontalDistance * 0.2,
			deltaZ,
			if (horizontalDistance <= 2.0) 0.45f else 0.75f,
			8.0f,
		)
		level.playSound(null, witch.x, witch.y, witch.z, SoundEvents.WITCH_THROW, witch.soundSource, 1.0f, 1.0f)
		return true
	}

	private const val MAX_FLANK_FAILURES = 2
}
