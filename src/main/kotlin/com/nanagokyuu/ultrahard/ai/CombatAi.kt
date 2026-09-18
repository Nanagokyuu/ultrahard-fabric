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
/** 负责目标偏好、群体站位、盾牌绕行和近战接敌；不同兵种复用相同的水平几何规则。 */
internal object CombatAi {
	@JvmStatic
	fun tickZombie(zombie: Zombie) {
		val level = AiSupport.ultraHardLevel(zombie) ?: return
		if (!AiSupport.shouldUpdate(zombie)) return
		val target = zombie.target ?: return
		if (!target.isAlive) return

		val config = UltraHardConfigs.values
		val zombies = level.getEntities(
			null,
			target.boundingBox.inflate(config.zombieCoordinationRadius),
		) { entity -> entity is Zombie && entity.target === target && entity.isAlive }
			.filterIsInstance<Zombie>()
			.plus(zombie)
			.distinctBy { it.uuid }
			.sortedBy { it.uuid }

		// 按 UUID 排序后再分工，避免实体列表顺序变化导致僵尸每次更新都换位。
		val facing = AiSupport.horizontalDirection(target.lookAngle)
		val zombieIndex = zombies.indexOf(zombie).coerceAtLeast(0)
		val frontGroup = maxOf(1, (zombies.size + 1) / 2)
		val isFrontliner = zombieIndex < frontGroup
		val armedFrontlinerExists = zombies.any {
			it !== zombie && !it.mainHandItem.isEmpty && it.distanceToSqr(target) <= config.zombieFrontDistance * config.zombieFrontDistance * 4.0
		}
		// 未持武器者退得更远，为靠近目标的持武器者让出攻击位置。
		val targetDistance = when {
			zombie.mainHandItem.isEmpty && armedFrontlinerExists -> config.zombieUnarmedYieldDistance
			isFrontliner -> config.zombieFrontDistance
			else -> config.zombieFlankDistance
		}
		// 前排从正面压迫，后排从背后或侧面接近；无武器僵尸主动给持武器者让位。
		val direction = if (isFrontliner && !(zombie.mainHandItem.isEmpty && armedFrontlinerExists)) facing else facing.scale(-1.0)
		AiSupport.moveTo(zombie, target.position().add(direction.scale(targetDistance)), config.zombieCoordinationSpeed)
	}

	@JvmStatic
	fun prioritizeUnshieldedTarget(mob: Mob) {
		val level = AiSupport.ultraHardLevel(mob) ?: return
		if (!AiSupport.isHostile(mob)) return
		val current = mob.target
		val players = level.getEntities(mob, mob.boundingBox.inflate(32.0)) { entity ->
			entity is ServerPlayer && entity.isAlive && !entity.isSpectator
		}.filterIsInstance<ServerPlayer>()
		// 只有在 32 格内找到更合适的无盾玩家时才切换目标，避免敌人频繁丢失原目标。
		val unshielded = players.filterNot { it.isBlocking }.minByOrNull { it.distanceToSqr(mob) }
		if (unshielded != null && (current !is ServerPlayer || current.isBlocking
			|| unshielded.distanceToSqr(mob) < current.distanceToSqr(mob) * 0.8)) {
			// 所有敌对生物共享同一套目标偏好：优先攻击没有举盾的玩家。
			mob.target = unshielded
		}
	}

	/**
	 * 仅当双方均为敌对生物，且配置范围内存在存活的非旁观玩家时抑制报复目标。
	 * 返回值供目标设置或受伤记忆注入使用，不取消伤害本身，也不表示所有场景都禁止内斗。
	 */
	@JvmStatic
	fun shouldIgnoreHostileRetaliation(mob: Mob, target: LivingEntity): Boolean {
		val level = AiSupport.ultraHardLevel(mob) ?: return false
		if (target === mob || target !is Mob || !AiSupport.isHostile(mob) || !AiSupport.isHostile(target)) return false
		val radius = UltraHardConfigs.values.hostileRetaliationSuppressionRadius
		// 玩家在场时，敌对生物之间的误伤只造成伤害，不把战斗目标转移给误伤者。
		return level.getEntities(mob, mob.boundingBox.inflate(radius)) { entity ->
			entity is ServerPlayer && entity.isAlive && !entity.isSpectator
		}.isNotEmpty()
	}

	@JvmStatic
	fun tickSkeleton(skeleton: AbstractSkeleton) {
		if (AiSupport.ultraHardLevel(skeleton) == null || !AiSupport.shouldUpdate(skeleton)) return
		val target = skeleton.target ?: return
		if (shouldFlankShield(skeleton, target)) {
			flankShield(skeleton, target)
			return
		}
		if (target.distanceToSqr(skeleton) < UltraHardConfigs.values.skeletonMinimumDistance.let { it * it }) {
			retreatSkeleton(skeleton, target)
		}
	}

	@JvmStatic
	fun shouldFlankShield(mob: Mob, target: LivingEntity): Boolean {
		if (AiSupport.ultraHardLevel(mob) == null || target !is Player || !target.isBlocking) return false
		val relative = AiSupport.horizontalDirection(mob.position().subtract(target.position()))
		// 玩家朝向向量与骷髅位置同向时表示骷髅在玩家前方，此时必须继续绕行。
		return AiSupport.shieldFacing(target).dot(relative) >= -0.25
	}

	/**
	 * 先尝试实体所在侧的绕行点，导航失败时尝试另一侧。
	 * 仍在盾牌正前方时先横移，越过侧面后再向后方推进，避免路径直接穿过玩家。
	 */
	@JvmStatic
	fun flankShield(mob: Mob, target: LivingEntity, radius: Double, speed: Double) {
		if (!shouldFlankShield(mob, target)) return
		val facing = AiSupport.shieldFacing(target)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val relative = mob.position().subtract(target.position())
		val side = if (relative.dot(left) > 0.1) {
			if (relative.dot(left) > 0.0) 1.0 else -1.0
		} else if (mob.id % 2 == 0) 1.0 else -1.0
		for (direction in listOf(side, -side)) {
			val waypoint = if (AiSupport.horizontalDirection(relative).dot(facing) > 0.25) {
				left.scale(direction * radius)
			} else {
				facing.scale(-radius).add(left.scale(direction * radius * 0.5))
			}
			val destination = target.position().add(waypoint)
			if (mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)) return
		}
	}

	@JvmStatic
	fun tickSpider(spider: Spider) {
		// 蜘蛛优先从玩家视线外接近；玩家正面举盾时，直接切换为绕盾路线。
		if (!AiSupport.shouldUpdate(spider)) return
		val target = spider.target ?: return
		if (shouldFlankShield(spider, target)) {
			flankShield(spider, target, 3.0, 1.25)
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(spider) > 9.0) {
			AiSupport.ambushApproach(spider, target, 2.5, 1.25)
		}
	}

	@JvmStatic
	fun tickVindicator(vindicator: Vindicator) {
		// 卫道士不在盾牌正面连续挥斧，先通过侧后方接近制造攻击角度。
		if (!AiSupport.shouldUpdate(vindicator)) return
		val target = vindicator.target ?: return
		if (shouldFlankShield(vindicator, target)) {
			flankShield(vindicator, target, 3.0, 1.2)
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(vindicator) > 6.25) {
			AiSupport.ambushApproach(vindicator, target, 2.5, 1.2)
		}
	}

	@JvmStatic
	fun tickRavager(ravager: Ravager) {
		// 劫掠兽体型较大，使用更宽的侧翼路线，避免一直顶着盾牌冲撞。
		if (!AiSupport.shouldUpdate(ravager)) return
		val target = ravager.target ?: return
		if (shouldFlankShield(ravager, target)) {
			flankShield(ravager, target, 4.0, 1.1)
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(ravager) > 16.0) {
			AiSupport.ambushApproach(ravager, target, 3.5, 1.1)
		}
	}

	@JvmStatic
	fun tickEnderman(enderman: EnderMan) {
		val level = AiSupport.ultraHardLevel(enderman) ?: return
		val target = enderman.target as? Player ?: return
		if (!target.isAlive) return

		val playerToEnderman = AiSupport.horizontalDirection(enderman.position().subtract(target.position()))
		val playerFacingEnderman = AiSupport.horizontalDirection(target.lookAngle).dot(playerToEnderman) > 0.6
		val shieldedFront = shouldFlankShield(enderman, target)
		// 玩家直视或正面举盾时，末影人不正面硬打，而是瞬移到盲区。
		if (!playerFacingEnderman && !shieldedFront && AiSupport.isBlockingTarget(target)) return
		val now = level.gameTime
		if (AiSupport.endermanTeleportCooldowns[enderman.uuid]?.let { now < it } == true) return

		val facing = AiSupport.horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val side = if (enderman.id % 2 == 0) 1.0 else -1.0
		val radius = if (shieldedFront) 3.5 else 3.0
		val destinations = listOf(
			target.position().subtract(facing.scale(radius)).add(left.scale(side * 1.0)),
			target.position().subtract(facing.scale(radius)).add(left.scale(-side * 1.0)),
			target.position().add(left.scale(side * radius)),
		)
		for (destination in destinations) {
			if (AiSupport.tryEndermanTeleport(enderman, destination)) {
				AiSupport.endermanTeleportCooldowns[enderman.uuid] = now + 20L
				return
			}
		}
	}

	@JvmStatic
	fun tickSilverfish(silverfish: Silverfish) {
		if (!AiSupport.shouldUpdate(silverfish)) return
		val level = AiSupport.ultraHardLevel(silverfish) ?: return
		val target = silverfish.target ?: return
		val group = level.getEntities(silverfish, silverfish.boundingBox.inflate(8.0)) { entity ->
			entity is Silverfish && entity.target === target && entity.isAlive
		}.filterIsInstance<Silverfish>().plus(silverfish).distinctBy { it.uuid }.sortedBy { it.uuid }
		val index = group.indexOf(silverfish).coerceAtLeast(0)
		// 蠹虫保留少量正面牵制单位，其余单位从侧面和背后包围玩家。
		if (shouldFlankShield(silverfish, target)) {
			if (index > 0 || group.size == 1) flankShield(silverfish, target, 2.5, 1.3)
		} else if (!AiSupport.isBlockingTarget(target)) {
			AiSupport.swarmApproach(silverfish, target, index, group.size, 2.5, 1.3)
		}
	}

	@JvmStatic
	fun tickEndermite(endermite: Endermite) {
		if (!AiSupport.shouldUpdate(endermite)) return
		val target = endermite.target ?: return
		// 末影螨不瞬移，而是依靠高速侧移和频繁换位骚扰玩家。
		if (shouldFlankShield(endermite, target)) {
			flankShield(endermite, target, 2.5, 1.35)
		} else if (!AiSupport.isBlockingTarget(target)) {
			AiSupport.ambushApproach(endermite, target, 2.0, 1.35)
		}
	}

	fun shouldCancelShieldedMelee(mob: Mob, target: Entity): Boolean =
		target is LivingEntity && shouldFlankShield(mob, target)

	@JvmStatic
	fun retreatSkeleton(skeleton: AbstractSkeleton, target: LivingEntity) {
		if (AiSupport.ultraHardLevel(skeleton) == null) return
		val config = UltraHardConfigs.values
		val away = AiSupport.horizontalDirection(skeleton.position().subtract(target.position()))
		val left = Vec3(-away.z, 0.0, away.x)
		// 先尝试正后方；路径不可达时改走左右侧，避免被身后的墙困住。
		val candidates = listOf(away, left, left.scale(-1.0))
		for (direction in candidates) {
			val destination = skeleton.position().add(direction.scale(config.skeletonRetreatDistance))
			if (skeleton.navigation.moveTo(destination.x, destination.y, destination.z, config.skeletonRetreatSpeed)) {
				return
			}
		}
	}

	internal fun flankShield(skeleton: AbstractSkeleton, target: LivingEntity) {
		if (!shouldFlankShield(skeleton, target) || !AiSupport.shouldUpdate(skeleton)) return
		val config = UltraHardConfigs.values
		flankShield(skeleton, target, maxOf(config.skeletonMinimumDistance + 1.0, config.skeletonRetreatDistance), config.skeletonRetreatSpeed)
	}
}
