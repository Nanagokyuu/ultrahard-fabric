package com.nanagokyuu.ultrahard

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

object UltraHardAi {
	/** 女巫每次近身遭遇的药水顺序，避免每个 tick 都重新从虚弱开始。 */
	// 记录同一女巫对近身玩家的药水投掷阶段。
	private val witchPotionSteps = HashMap<UUID, Int>()
	/** 玩家维度的蜘蛛网冷却；键只使用玩家 UUID，保证跨维度也不会立即重复触发。 */
	private val spiderWebCooldowns = HashMap<UUID, Long>()
	/** 按维度保存临时蛛网，防止不同维度的相同坐标互相影响。 */
	private val temporarySpiderWebs = HashMap<ServerLevel, HashMap<BlockPos, Long>>()
	/** 末影人主动瞬移的冷却，避免它在每个 AI tick 中连续换位。 */
	private val endermanTeleportCooldowns = HashMap<UUID, Long>()

	@JvmStatic
	fun tickZombie(zombie: Zombie) {
		val level = ultraHardLevel(zombie) ?: return
		if (!shouldUpdate(zombie)) return
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
		val facing = horizontalDirection(target.lookAngle)
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
		moveTo(zombie, target.position().add(direction.scale(targetDistance)), config.zombieCoordinationSpeed)
	}

	@JvmStatic
	fun prioritizeUnshieldedTarget(mob: Mob) {
		val level = ultraHardLevel(mob) ?: return
		if (!isHostile(mob)) return
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

	@JvmStatic
	fun shouldIgnoreHostileRetaliation(mob: Mob, target: LivingEntity): Boolean {
		val level = ultraHardLevel(mob) ?: return false
		if (target === mob || target !is Mob || !isHostile(mob) || !isHostile(target)) return false
		val radius = UltraHardConfigs.values.hostileRetaliationSuppressionRadius
		// 玩家在场时，敌对生物之间的误伤只造成伤害，不把战斗目标转移给误伤者。
		return level.getEntities(mob, mob.boundingBox.inflate(radius)) { entity ->
			entity is ServerPlayer && entity.isAlive && !entity.isSpectator
		}.isNotEmpty()
	}

	@JvmStatic
	fun tickSkeleton(skeleton: AbstractSkeleton) {
		if (ultraHardLevel(skeleton) == null || !shouldUpdate(skeleton)) return
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
		if (ultraHardLevel(mob) == null || target !is Player || !target.isBlocking) return false
		val relative = horizontalDirection(mob.position().subtract(target.position()))
		// 玩家朝向向量与骷髅位置同向时表示骷髅在玩家前方，此时必须继续绕行。
		return shieldFacing(target).dot(relative) >= -0.25
	}

	@JvmStatic
	fun flankShield(skeleton: AbstractSkeleton, target: LivingEntity) {
		if (!shouldFlankShield(skeleton, target) || !shouldUpdate(skeleton)) return
		val config = UltraHardConfigs.values
		flankShield(skeleton, target, maxOf(config.skeletonMinimumDistance + 1.0, config.skeletonRetreatDistance), config.skeletonRetreatSpeed)
	}

	@JvmStatic
	fun flankShield(mob: Mob, target: LivingEntity, radius: Double, speed: Double) {
		if (!shouldFlankShield(mob, target)) return
		val facing = shieldFacing(target)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val relative = mob.position().subtract(target.position())
		val side = if (relative.dot(left) > 0.1) {
			if (relative.dot(left) > 0.0) 1.0 else -1.0
		} else if (mob.id % 2 == 0) 1.0 else -1.0
		for (direction in listOf(side, -side)) {
			val waypoint = if (horizontalDirection(relative).dot(facing) > 0.25) {
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
		if (!shouldUpdate(spider)) return
		val target = spider.target ?: return
		if (shouldFlankShield(spider, target)) {
			flankShield(spider, target, 3.0, 1.25)
		} else if (!isBlockingTarget(target) && target.distanceToSqr(spider) > 9.0) {
			ambushApproach(spider, target, 2.5, 1.25)
		}
	}

	@JvmStatic
	fun tickVindicator(vindicator: Vindicator) {
		// 卫道士不在盾牌正面连续挥斧，先通过侧后方接近制造攻击角度。
		if (!shouldUpdate(vindicator)) return
		val target = vindicator.target ?: return
		if (shouldFlankShield(vindicator, target)) {
			flankShield(vindicator, target, 3.0, 1.2)
		} else if (!isBlockingTarget(target) && target.distanceToSqr(vindicator) > 6.25) {
			ambushApproach(vindicator, target, 2.5, 1.2)
		}
	}

	@JvmStatic
	fun tickRavager(ravager: Ravager) {
		// 劫掠兽体型较大，使用更宽的侧翼路线，避免一直顶着盾牌冲撞。
		if (!shouldUpdate(ravager)) return
		val target = ravager.target ?: return
		if (shouldFlankShield(ravager, target)) {
			flankShield(ravager, target, 4.0, 1.1)
		} else if (!isBlockingTarget(target) && target.distanceToSqr(ravager) > 16.0) {
			ambushApproach(ravager, target, 3.5, 1.1)
		}
	}

	@JvmStatic
	fun tickEnderman(enderman: EnderMan) {
		val level = ultraHardLevel(enderman) ?: return
		val target = enderman.target as? Player ?: return
		if (!target.isAlive) return

		val playerToEnderman = horizontalDirection(enderman.position().subtract(target.position()))
		val playerFacingEnderman = horizontalDirection(target.lookAngle).dot(playerToEnderman) > 0.6
		val shieldedFront = shouldFlankShield(enderman, target)
		// 玩家直视或正面举盾时，末影人不正面硬打，而是瞬移到盲区。
		if (!playerFacingEnderman && !shieldedFront && isBlockingTarget(target)) return
		val now = level.gameTime
		if (endermanTeleportCooldowns[enderman.uuid]?.let { now < it } == true) return

		val facing = horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val side = if (enderman.id % 2 == 0) 1.0 else -1.0
		val radius = if (shieldedFront) 3.5 else 3.0
		val destinations = listOf(
			target.position().subtract(facing.scale(radius)).add(left.scale(side * 1.0)),
			target.position().subtract(facing.scale(radius)).add(left.scale(-side * 1.0)),
			target.position().add(left.scale(side * radius)),
		)
		for (destination in destinations) {
			if (tryEndermanTeleport(enderman, destination)) {
				endermanTeleportCooldowns[enderman.uuid] = now + 20L
				return
			}
		}
	}

	@JvmStatic
	fun tickSilverfish(silverfish: Silverfish) {
		if (!shouldUpdate(silverfish)) return
		val level = ultraHardLevel(silverfish) ?: return
		val target = silverfish.target ?: return
		val group = level.getEntities(silverfish, silverfish.boundingBox.inflate(8.0)) { entity ->
			entity is Silverfish && entity.target === target && entity.isAlive
		}.filterIsInstance<Silverfish>().plus(silverfish).distinctBy { it.uuid }.sortedBy { it.uuid }
		val index = group.indexOf(silverfish).coerceAtLeast(0)
		// 蠹虫保留少量正面牵制单位，其余单位从侧面和背后包围玩家。
		if (shouldFlankShield(silverfish, target)) {
			if (index > 0 || group.size == 1) flankShield(silverfish, target, 2.5, 1.3)
		} else if (!isBlockingTarget(target)) {
			swarmApproach(silverfish, target, index, group.size, 2.5, 1.3)
		}
	}

	@JvmStatic
	fun tickEndermite(endermite: Endermite) {
		if (!shouldUpdate(endermite)) return
		val target = endermite.target ?: return
		// 末影螨不瞬移，而是依靠高速侧移和频繁换位骚扰玩家。
		if (shouldFlankShield(endermite, target)) {
			flankShield(endermite, target, 2.5, 1.35)
		} else if (!isBlockingTarget(target)) {
			ambushApproach(endermite, target, 2.0, 1.35)
		}
	}

	private fun shieldFacing(target: LivingEntity): Vec3 {
		// 使用水平朝向，玩家抬头或低头时仍有稳定的前后判定。
		val yaw = Math.toRadians(target.yRot.toDouble())
		return Vec3(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
	}

	@JvmStatic
	fun tickCreeperShield(creeper: Creeper) {
		val level = ultraHardLevel(creeper) ?: return
		if (!shouldFlankCreeperShield(creeper)) return
		val target = creeper.target as Player

		val toTarget = horizontalDirection(target.position().subtract(creeper.position()))
		// 只有苦力怕确实朝向玩家时，举盾才会让它改变战术。
		if (horizontalDirection(creeper.lookAngle).dot(toTarget) < 0.25) return
		val playerToCreeper = toTarget.scale(-1.0)
		val facing = shieldFacing(target)
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
		ultraHardLevel(creeper) ?: return false
		val target = creeper.target as? Player ?: return false
		if (!target.isAlive || !target.isBlocking || creeper.swellDir <= 0) return false

		val toTarget = horizontalDirection(target.position().subtract(creeper.position()))
		// 只有苦力怕确实朝向玩家时，举盾才会让它改变战术。
		if (horizontalDirection(creeper.lookAngle).dot(toTarget) < 0.25) return false
		val playerToCreeper = toTarget.scale(-1.0)
		return shieldFacing(target).dot(playerToCreeper) >= -0.25
	}

	@JvmStatic
	fun retreatSkeleton(skeleton: AbstractSkeleton, target: LivingEntity) {
		if (ultraHardLevel(skeleton) == null) return
		val config = UltraHardConfigs.values
		val away = horizontalDirection(skeleton.position().subtract(target.position()))
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

	@JvmStatic
	fun tickCreeper(creeper: Creeper) {
		val level = ultraHardLevel(creeper) ?: return
		if (creeper.swellDir <= 0 || !shouldUpdate(creeper)) return
		val config = UltraHardConfigs.values
		// 仅在苦力怕已经开始引爆后才打断其他敌对生物的原有寻路。
		for (entity in level.getEntities(creeper, creeper.boundingBox.inflate(config.creeperEvacuationRadius)) { it !== creeper }) {
			val mob = entity as? Mob ?: continue
			if (!isHostile(mob) || mob is Creeper) continue
			val direction = horizontalDirection(mob.position().subtract(creeper.position()))
			moveTo(mob, creeper.position().add(direction.scale(config.creeperEvacuationDistance)), config.creeperEvacuationSpeed)
		}
	}

	@JvmStatic
	fun tickWitch(witch: Witch) {
		val level = ultraHardLevel(witch) ?: return
		if (!shouldUpdate(witch)) return
		val config = UltraHardConfigs.values
		// 受伤比例最低的袭击单位优先获得治疗，覆盖原本对玩家的进攻目标。
		val woundedRaider = level.getEntities(witch, witch.boundingBox.inflate(config.witchHealRadius)) { entity ->
			entity is Raider && entity !== witch && entity.isAlive && isWitchHealingPriority(entity) && entity.health < entity.maxHealth
		}.filterIsInstance<Raider>().minByOrNull { it.health / it.maxHealth }
		if (woundedRaider != null) {
			witch.target = woundedRaider
			return
		}

		val player = witch.target as? ServerPlayer ?: return
		if (shouldFlankShield(witch, player)) {
			witch.stopUsingItem()
			flankShield(witch, player, config.witchBacklineDistance, config.witchRetreatSpeed)
			return
		}
		if (player.distanceToSqr(witch) < config.witchBacklineDistance * config.witchBacklineDistance) {
			val away = horizontalDirection(witch.position().subtract(player.position()))
			moveTo(witch, witch.position().add(away.scale(config.witchBacklineDistance)), config.witchRetreatSpeed)
		}
	}

	@JvmStatic
	/** 让掠夺者在盾牌正面停火，并从侧后方重新寻找射击角度。 */
	fun tickPillager(pillager: Pillager) {
		if (!shouldUpdate(pillager)) return
		val target = pillager.target ?: return
		if (shouldFlankShield(pillager, target)) {
			pillager.stopUsingItem()
			flankShield(pillager, target, 8.0, 1.15)
		} else if (!isBlockingTarget(target) && target.distanceToSqr(pillager) < 36.0) {
			ambushApproach(pillager, target, 8.0, 1.15)
		}
	}

	@JvmStatic
	/** 让唤魔者远离盾牌正面，利用侧后方位置施放控制法术。 */
	fun tickEvoker(evoker: Evoker) {
		if (!shouldUpdate(evoker)) return
		val target = evoker.target ?: return
		if (shouldFlankShield(evoker, target)) {
			flankShield(evoker, target, 7.0, 1.1)
		} else if (!isBlockingTarget(target) && target.distanceToSqr(evoker) < 64.0) {
			ambushApproach(evoker, target, 7.0, 1.1)
		}
	}

	@JvmStatic
	/** 判断近战攻击是否正好落在玩家盾牌覆盖的正面区域。 */
	fun shouldCancelShieldedMelee(mob: Mob, target: Entity): Boolean =
		target is LivingEntity && shouldFlankShield(mob, target)

	/** 判断目标是否正在举盾，用于在未举盾时启用主动偷袭路线。 */
	private fun isBlockingTarget(target: LivingEntity): Boolean = target is Player && target.isBlocking

	/** 按群体编号给小型怪物分配正面、侧面和背后的位置，避免全部挤在同一格。 */
	private fun swarmApproach(mob: Mob, target: LivingEntity, index: Int, size: Int, radius: Double, speed: Double) {
		val facing = horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val destination = if (size > 1 && index == 0) {
			target.position().add(facing.scale(radius * 0.8))
		} else {
			val side = if (index % 2 == 0) 1.0 else -1.0
			target.position().subtract(facing.scale(radius)).add(left.scale(side * radius * (0.5 + index / 3.0)))
		}
		mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)
	}

	/** 在末影人瞬移前检查落点，避免进入水、危险方块或实体无法站立的位置。 */
	private fun tryEndermanTeleport(enderman: EnderMan, destination: Vec3): Boolean {
		val level = ultraHardLevel(enderman) ?: return false
		val pos = BlockPos.containing(destination.x, destination.y, destination.z)
		if (!level.getBlockState(pos).isAir || !level.getBlockState(pos.above()).isAir || !level.getBlockState(pos.above(2)).isAir) return false
		if (!level.getFluidState(pos).isEmpty || !level.getFluidState(pos.above()).isEmpty) return false
		if (level.getBlockState(pos.below()).isAir) return false
		val offset = Vec3.atBottomCenterOf(pos).subtract(enderman.position())
		if (!level.noCollision(enderman, enderman.boundingBox.move(offset.x, offset.y, offset.z))) return false
		return enderman.teleportTo(level, destination.x, pos.y.toDouble(), destination.z, emptySet(), enderman.yRot, enderman.xRot, false)
	}

	/** 计算玩家视线后的侧后方落点，让怪物在接敌前优先绕到盲区。 */
	private fun ambushApproach(mob: Mob, target: LivingEntity, radius: Double, speed: Double) {
		val facing = horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val side = if (mob.id % 2 == 0) 1.0 else -1.0
		val destination = target.position()
			.subtract(facing.scale(radius))
			.add(left.scale(side * radius * 0.45))
		mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)
	}

	@JvmStatic
	fun nextWitchPotion(witch: Witch, target: LivingEntity, original: Holder<Potion>): Holder<Potion> {
		if (ultraHardLevel(witch) == null || target !is ServerPlayer) return original
		val maxDistance = UltraHardConfigs.values.witchBacklineDistance
		if (target.distanceToSqr(witch) > maxDistance * maxDistance) return original

		// 近身遭遇固定先削弱、再减速，之后持续使用伤害药水。
		val step = witchPotionSteps.getOrDefault(witch.uuid, 0)
		witchPotionSteps[witch.uuid] = step + 1
		return when (step) {
			0 -> Potions.WEAKNESS
			1 -> Potions.SLOWNESS
			else -> Potions.HARMING
		}
	}

	@JvmStatic
	fun performCloseRangeAttack(witch: Witch, target: LivingEntity): Boolean {
		val level = ultraHardLevel(witch) ?: return false
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

	@JvmStatic
	fun tickUndeadShelter(mob: Mob) {
		val level = ultraHardLevel(mob) ?: return
		val config = UltraHardConfigs.values
		if (!mob.type.builtInRegistryHolder().`is`(EntityTypeTags.UNDEAD)
			|| mob.isInWater || !level.isBrightOutside || !level.canSeeSky(mob.blockPosition())) return
		if (mob.tickCount % config.undeadShelterSearchIntervalTicks != 0) return

		// 以最近的水源或不能直视天空的可站立空气方块作为避阳终点。
		val center = mob.blockPosition()
		var best: BlockPos? = null
		var bestDistance = Double.MAX_VALUE
		for (xOffset in -config.undeadShelterSearchRadius..config.undeadShelterSearchRadius) {
			for (zOffset in -config.undeadShelterSearchRadius..config.undeadShelterSearchRadius) {
				for (yOffset in -1..2) {
					val candidate = center.offset(xOffset, yOffset, zOffset)
					if (!level.isLoaded(candidate)) continue
					val isWater = level.getFluidState(candidate).`is`(FluidTags.WATER)
					val isShade = level.getBlockState(candidate).isAir && !level.getBlockState(candidate.below()).isAir && !level.canSeeSky(candidate)
					if (!isWater && !isShade) continue
					val distance = candidate.distSqr(center)
					if (distance < bestDistance) {
						best = candidate
						bestDistance = distance
					}
				}
			}
		}
		best?.let { moveTo(mob, Vec3.atBottomCenterOf(it), config.undeadShelterSpeed) }
	}

	private fun ultraHardLevel(mob: Mob): ServerLevel? {
		val level = mob.level() as? ServerLevel ?: return null
		return level.takeIf(UltraHardDifficulties::isUltraHard)
	}

	private fun shouldUpdate(mob: Mob): Boolean = mob.tickCount % UltraHardConfigs.values.aiUpdateIntervalTicks == 0

	private fun isHostile(entity: Entity): Boolean = entity is Enemy || entity is Monster

	private fun isWitchHealingPriority(entity: Entity): Boolean =
		entity is Pillager || entity is Vindicator || entity is Evoker || entity is Ravager

	private fun moveTo(mob: Mob, destination: Vec3, speed: Double) {
		mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)
	}

	private fun horizontalDirection(vector: Vec3): Vec3 {
		val horizontal = Vec3(vector.x, 0.0, vector.z)
		return if (horizontal.lengthSqr() < 1.0E-4) Vec3(1.0, 0.0, 0.0) else horizontal.normalize()
	}

	@JvmStatic
	fun placeSpiderWeb(level: ServerLevel, player: ServerPlayer) {
		val now = level.gameTime
		if (spiderWebCooldowns[player.uuid]?.let { now < it } == true) return
		val pos = player.blockPosition()
		if (!level.getBlockState(pos).isAir) return

		// 蜘蛛网只作为短暂控制技存在，并给同一玩家留出明确的脱困窗口。
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 3)
		spiderWebCooldowns[player.uuid] = now + SPIDER_WEB_COOLDOWN_TICKS
		temporarySpiderWebs.getOrPut(level) { HashMap() }[pos] = now + SPIDER_WEB_DURATION_TICKS
	}

	@JvmStatic
	fun tickTemporarySpiderWebs(level: ServerLevel) {
		val now = level.gameTime
		val webs = temporarySpiderWebs[level] ?: return
		// 先复制过期坐标再删除，避免遍历 Map 时修改集合。
		val expired = webs.filterValues { it <= now }.keys.toList()
		for (pos in expired) {
			if (level.getBlockState(pos).`is`(net.minecraft.world.level.block.Blocks.COBWEB)) {
				level.removeBlock(pos, false)
			}
			webs.remove(pos)
		}
		if (webs.isEmpty()) temporarySpiderWebs.remove(level)
		spiderWebCooldowns.entries.removeIf { it.value <= now - SPIDER_WEB_COOLDOWN_TICKS }
	}

	private const val SPIDER_WEB_COOLDOWN_TICKS = 200L
	private const val SPIDER_WEB_DURATION_TICKS = 120L
}
