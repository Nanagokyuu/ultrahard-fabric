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
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.monster.illager.Pillager
import net.minecraft.world.entity.monster.illager.Vindicator
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.zombie.Zombie
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
	fun shouldFlankShield(skeleton: AbstractSkeleton, target: LivingEntity): Boolean {
		if (ultraHardLevel(skeleton) == null || target !is Player || !target.isBlocking) return false
		val relative = horizontalDirection(skeleton.position().subtract(target.position()))
		// 玩家朝向向量与骷髅位置同向时表示骷髅在玩家前方，此时必须继续绕行。
		return shieldFacing(target).dot(relative) >= -0.25
	}

	@JvmStatic
	fun flankShield(skeleton: AbstractSkeleton, target: LivingEntity) {
		if (!shouldFlankShield(skeleton, target) || !shouldUpdate(skeleton)) return
		val config = UltraHardConfigs.values
		val facing = shieldFacing(target)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val relative = skeleton.position().subtract(target.position())
		val radius = maxOf(config.skeletonMinimumDistance + 1.0, config.skeletonRetreatDistance)
		// 优先沿当前所在侧绕行；正前方的多只骷髅按实体 ID 分到两侧，避免互相堵路。
		val side = if (kotlin.math.abs(relative.dot(left)) > 0.1) {
			if (relative.dot(left) > 0.0) 1.0 else -1.0
		} else {
			if (skeleton.id % 2 == 0) 1.0 else -1.0
		}
		for (direction in listOf(side, -side)) {
			val waypoint = if (horizontalDirection(relative).dot(facing) > 0.25) {
				left.scale(direction * radius)
			} else {
				facing.scale(-radius).add(left.scale(direction * radius * 0.5))
			}
			val destination = target.position().add(waypoint)
			if (skeleton.navigation.moveTo(destination.x, destination.y, destination.z, config.skeletonRetreatSpeed)) return
		}
	}

	private fun shieldFacing(target: LivingEntity): Vec3 {
		// 使用水平朝向，玩家抬头或低头时仍有稳定的前后判定。
		val yaw = Math.toRadians(target.yRot.toDouble())
		return Vec3(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
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
		if (player.distanceToSqr(witch) < config.witchBacklineDistance * config.witchBacklineDistance) {
			val away = horizontalDirection(witch.position().subtract(player.position()))
			moveTo(witch, witch.position().add(away.scale(config.witchBacklineDistance)), config.witchRetreatSpeed)
		}
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

	private fun isHostile(entity: Entity): Boolean = entity is Monster

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
