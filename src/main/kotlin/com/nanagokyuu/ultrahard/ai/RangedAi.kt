package com.nanagokyuu.ultrahard.ai

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.EntityTypeTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Endermite
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.monster.illager.Pillager
import net.minecraft.world.entity.monster.illager.Vindicator
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
import net.minecraft.world.entity.raid.Raider
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.phys.Vec3
import java.util.UUID

import com.nanagokyuu.ultrahard.UltraHardConfigs
import com.nanagokyuu.ultrahard.UltraHardDifficulties
/**
 * 管理苦力怕引爆前的绕盾与疏散，以及女巫、掠夺者、唤魔者的站位和施法行为。
 * 药水弹道保留原版发射方式；只在满足近身玩家条件时接管女巫药水选择。
 */
internal object RangedAi {
	@JvmStatic
	fun tickCreeperShield(creeper: Creeper) {
		val level = AiSupport.ultraHardLevel(creeper) ?: return
		if (!shouldFlankCreeperShield(creeper)) return
		val target = creeper.target as Player

		val toTarget = AiSupport.horizontalDirection(target.position().subtract(creeper.position()))
		// 只有苦力怕确实朝向玩家时，举盾才会让它改变战术。
		if (AiSupport.horizontalDirection(creeper.lookAngle).dot(toTarget) < 0.25) return
		val playerToCreeper = toTarget.scale(-1.0)
		val facing = AiSupport.shieldFacing(target)
		if (facing.dot(playerToCreeper) < -0.25) return

		// 在 tick 开始处重置引爆计时，避免本 tick 的原版爆炸逻辑继续执行。
		creeper.swellDir = -1
		val config = UltraHardConfigs.values
		val left = Vec3(-facing.z, 0.0, facing.x)
		val radius = config.creeperEvacuationDistance.coerceAtLeast(3.0)
		val behind = target.position().subtract(facing.scale(radius))
		val relative = creeper.position().subtract(target.position())
		val side = if (relative.dot(left) >= 0.0) 1.0 else -1.0
		val candidates = listOf(
			behind,
			target.position().add(facing.scale(-radius * 0.7)).add(left.scale(side * radius * 0.7)),
			target.position().add(facing.scale(-radius * 0.7)).add(left.scale(-side * radius * 0.7)),
		)
		for (destination in candidates) {
			if (creeper.navigation.moveTo(destination.x, destination.y, destination.z, config.creeperEvacuationSpeed)) return
		}
	}

	@JvmStatic
	fun shouldFlankCreeperShield(creeper: Creeper): Boolean {
		AiSupport.ultraHardLevel(creeper) ?: return false
		val target = creeper.target as? Player ?: return false
		if (!target.isAlive || !target.isBlocking || creeper.swellDir <= 0) return false

		val toTarget = AiSupport.horizontalDirection(target.position().subtract(creeper.position()))
		// 只有苦力怕确实朝向玩家时，举盾才会让它改变战术。
		if (AiSupport.horizontalDirection(creeper.lookAngle).dot(toTarget) < 0.25) return false
		val playerToCreeper = toTarget.scale(-1.0)
		return AiSupport.shieldFacing(target).dot(playerToCreeper) >= -0.25
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
		if (CombatAi.shouldFlankShield(witch, player)) {
			witch.stopUsingItem()
			CombatAi.flankShield(witch, player, config.witchBacklineDistance, config.witchRetreatSpeed)
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
			CombatAi.flankShield(pillager, target, 8.0, 1.15)
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(pillager) < 36.0) {
			AiSupport.ambushApproach(pillager, target, 8.0, 1.15)
		}
	}

	fun tickEvoker(evoker: Evoker) {
		if (!AiSupport.shouldUpdate(evoker)) return
		val target = evoker.target ?: return
		if (CombatAi.shouldFlankShield(evoker, target)) {
			CombatAi.flankShield(evoker, target, 7.0, 1.1)
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
}
