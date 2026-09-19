package com.nanagokyuu.ultrahard.ai.combat

import com.nanagokyuu.ultrahard.ai.AiSupport
import com.nanagokyuu.ultrahard.ai.WorldAi
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.WeakHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Endermite
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.monster.illager.Pillager
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** 负责目标偏好、群体站位、盾牌绕行和近战接敌；不同兵种复用相同的水平几何规则。 */
internal object CombatAi {
	/** 弱引用键避免死亡或卸载的生物残留；三秒无进展后正面施压五秒。 */
	private data class FlankState(
		val targetId: UUID,
		var startedAt: Long,
		var checkedAt: Long = Long.MIN_VALUE,
		var frontalUntil: Long = Long.MIN_VALUE,
		var path: Path? = null,
		var lastProgressAt: Long = startedAt,
		var bestFacingDot: Double = 1.0,
		var pressureUpdatedAt: Long = Long.MIN_VALUE,
		var retreatRetryAt: Long = Long.MIN_VALUE,
		var formationDestination: Vec3? = null,
		var bestFormationDistance: Double = Double.MAX_VALUE,
		var positioned: Boolean = false,
	)
	private val flankStates = WeakHashMap<Mob, FlankState>()
	private data class ZombieGroup(val tick: Long, val origin: Vec3, val members: List<WeakReference<Zombie>>)
	private val zombieGroups = WeakHashMap<LivingEntity, ZombieGroup>()
	private const val FLANK_TIMEOUT_TICKS = 60L
	private const val FRONTAL_PRESSURE_TICKS = 100L

	@JvmStatic
	fun tickZombie(zombie: Zombie) {
		val level = AiSupport.ultraHardLevel(zombie) ?: return
		if (!AiSupport.shouldUpdate(zombie)) return
		val target = zombie.target ?: return
		if (!target.isAlive) return

		val config = UltraHardConfigs.values
		// 混编由独立任务接管，避免这里的旧编队导航打断换位或原版近战出手。
		if (MixedUndeadFormation.direction(zombie, target) != null) return
		// 同一目标周围的僵尸共享十刻编队快照，弱引用成员不会反向保留目标实体。
		val now = level.gameTime
		var group = zombieGroups[target]
		if (group == null || now - group.tick >= 10L || now < group.tick || target.position().distanceToSqr(group.origin) >= 4.0) {
			val members = level.getEntities(null, target.boundingBox.inflate(config.zombieCoordinationRadius)) {
				it is Zombie && it.target === target && it.isAlive
			}.filterIsInstance<Zombie>().sortedBy { it.uuid }.map { WeakReference(it) }
			group = ZombieGroup(now, target.position(), members)
			zombieGroups[target] = group
		}
		val zombies = group.members.mapNotNull { it.get() }.filter { it.isAlive && it.target === target && it.level() === level }
			.let { members -> if (zombie in members) members else (members + zombie).sortedBy { it.uuid } }

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
		AiSupport.approachOrPressure(zombie, target.position().add(direction.scale(targetDistance)), config.zombieCoordinationSpeed)
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
		// 只把未持弓的凋灵骷髅交给专属近战；持弓者完整复用普通骷髅的站位与退避。
		if (skeleton is WitherSkeleton && !isBowSkeleton(skeleton)) {
			// 换回近战时清掉之前的弓箭绕行/正面施压缓存，避免再次拿弓时继承旧导航。
			flankStates.remove(skeleton)
			return
		}
		if (AiSupport.ultraHardLevel(skeleton) == null || !AiSupport.shouldUpdate(skeleton)) return
		val target = skeleton.target ?: return
		if (shouldFlankShield(skeleton, target)) {
			flankShield(skeleton, target)
			return
		}
		// 远程单位恢复正面射击，但不因此放弃射程优势贴脸追击。
		if (maintainFrontalPressure(skeleton, target)) return
		if (target.distanceToSqr(skeleton) < UltraHardConfigs.values.skeletonMinimumDistance.let { it * it }) {
			retreatSkeleton(skeleton, target)
		}
	}

	/** 持弓骷髅主动进入包围站位，其它兵种仍仅在玩家举盾时尝试绕后。 */
	@JvmStatic
	fun shouldFlankShield(mob: Mob, target: LivingEntity): Boolean {
		if (mob is WitherSkeleton && !isBowSkeleton(mob)) return false
		if (mob is Zombie && MixedUndeadFormation.direction(mob, target) != null) return false
		val bowSkeleton = isBowSkeleton(mob)
		// 弓箭即使没有正对玩家，只要射线会穿过友军，也先绕开挡箭的僵尸或骷髅。
		return shouldFlankTarget(mob, target, !bowSkeleton, if (bowSkeleton) 0.25 else -0.25) ||
			(bowSkeleton && hasFriendlyFireRisk(mob, target) && shouldFlankTarget(mob, target, false, -1.0))
	}

	/**
	 * 用箭矢预期的起点到目标眼睛做短步长采样。
	 * 原版弹道会受重力影响，但近距离友军遮挡是主要误伤来源，水平射线足以筛掉危险方向。
	 */
	private fun hasFriendlyFireRisk(skeleton: Mob, target: LivingEntity): Boolean {
		val start = skeleton.eyePosition
		val end = target.eyePosition
		val delta = end.subtract(start)
		val distance = delta.length()
		if (distance <= 0.5) return false
		val steps = kotlin.math.ceil(distance / 0.2).toInt()
		val candidates = skeleton.level().getEntities(skeleton, skeleton.boundingBox.expandTowards(delta).inflate(0.65)) {
			it !== target && it.isAlive && (it is Zombie || it is AbstractSkeleton)
		}
		return candidates.any { ally ->
			for (step in 1 until steps) {
				val point = start.add(delta.scale(step.toDouble() / steps))
				if (ally.boundingBox.inflate(0.12).contains(point)) return@any true
			}
			false
		}
	}

	private fun isBowSkeleton(mob: Mob): Boolean = AiSupport.isBowSkeleton(mob)

	private fun shouldFlankTarget(
		mob: Mob,
		target: LivingEntity,
		requireShield: Boolean,
		frontThreshold: Double,
	): Boolean {
		val level = AiSupport.ultraHardLevel(mob) ?: return false
		if (WorldAi.isSheltering(mob)) return false
		if (target !is Player || !target.isAlive || target.isCreative || target.isSpectator || (requireShield && !target.isBlocking)) {
			flankStates.remove(mob)
			return false
		}
		val relative = AiSupport.horizontalDirection(mob.position().subtract(target.position()))
		val facingDot = AiSupport.shieldFacing(target).dot(relative)
		val bowSkeleton = isBowSkeleton(mob)
		if (!bowSkeleton && facingDot < frontThreshold) {
			flankStates.remove(mob)
			return false
		}
		val now = level.gameTime
		val state = flankStates[mob]?.takeIf { it.targetId == target.uuid }
			?: FlankState(target.uuid, now).also { flankStates[mob] = it }
		if (now < state.frontalUntil) return false
		if (state.frontalUntil != Long.MIN_VALUE) {
			state.frontalUntil = Long.MIN_VALUE
			state.startedAt = now
			state.checkedAt = Long.MIN_VALUE
			state.lastProgressAt = now
			state.bestFacingDot = facingDot
			state.bestFormationDistance = Double.MAX_VALUE
		}
		if (bowSkeleton) {
			val radius = maxOf(UltraHardConfigs.values.skeletonMinimumDistance + 1.0, UltraHardConfigs.values.skeletonRetreatDistance)
			val destination = target.position().add(SkeletonFormation.direction(mob as AbstractSkeleton, target).scale(radius))
			val offset = mob.position().subtract(destination)
			val distance = offset.x * offset.x + offset.z * offset.z
			// 到位与离位使用不同阈值，允许原版小幅横移和蓄力，不因轻微抖动反复停射。
			if (distance <= (if (state.positioned) 9.0 else 2.25) && kotlin.math.abs(offset.y) <= 3.5 && mob.sensing.hasLineOfSight(target)) {
				if (!state.positioned) mob.navigation.stop()
				state.positioned = true
				state.path = null
				state.startedAt = now
				state.lastProgressAt = now
				state.checkedAt = Long.MIN_VALUE
				state.bestFormationDistance = Double.MAX_VALUE
				return false
			}
			state.positioned = false
			state.formationDestination = destination
			// 以离分配站位的距离衡量进展，绕到背后或正面牵制都不会被朝向点积误判。
			if (distance < state.bestFormationDistance - 0.5) {
				state.bestFormationDistance = distance
				state.lastProgressAt = now
			}
		}
		// 向背面推进时允许继续绕行；三秒无进展或总计六秒仍未完成才回退。
		if (!bowSkeleton && facingDot < state.bestFacingDot - 0.1) {
			state.bestFacingDot = facingDot
			state.lastProgressAt = now
		}
		if (now - state.lastProgressAt >= FLANK_TIMEOUT_TICKS || now - state.startedAt >= FLANK_TIMEOUT_TICKS * 2) {
			beginFrontalPressure(mob, target, state, now)
			return false
		}
		// 多个攻击与导航注入共用检查结果，避免每 tick 重复搜索同一条路线。
		if (state.checkedAt != Long.MIN_VALUE && now - state.checkedAt < UltraHardConfigs.values.aiUpdateIntervalTicks) {
			return state.path != null
		}
		state.checkedAt = now
		state.path = if (bowSkeleton) findFormationPath(mob, target, state.formationDestination!!) else findFlankPath(mob, target)
		if (state.path == null) beginFrontalPressure(mob, target, state, now)
		return state.path != null
	}

	private fun isFrontalPressureActive(mob: Mob): Boolean =
		flankStates[mob]?.let { it.targetId == mob.target?.uuid && mob.level().gameTime < it.frontalUntil } == true

	/** 只接受有支撑、无碰撞、有射界且完整可达的编队落点；在附近高度寻找地面。 */
	private fun findFormationPath(mob: Mob, target: LivingEntity, destination: Vec3): Path? {
		for (height in listOf(0, 1, -1, 2, -2, 3, -3)) {
			val block = BlockPos.containing(destination).offset(0, height, 0)
			val standing = Vec3(block.x + 0.5, block.y.toDouble(), block.z + 0.5)
			val floor = block.below()
			if (!mob.level().getBlockState(floor).isFaceSturdy(mob.level(), floor, Direction.UP)) continue
			if (!mob.level().noCollision(mob, mob.boundingBox.move(standing.subtract(mob.position())))) continue
			val eye = standing.add(0.0, mob.eyeHeight.toDouble(), 0.0)
			if (mob.level().clip(ClipContext(eye, target.eyePosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).type != HitResult.Type.MISS) continue
			val path = mob.navigation.createPath(block, 0)
			if (path != null && path.canReach()) return path
		}
		return null
	}

	/** 路径不可达或绕行超时都恢复原版攻击，不再取消挥击或蓄力。 */
	private fun beginFrontalPressure(mob: Mob, target: LivingEntity, state: FlankState, now: Long) {
		state.path = null
		state.frontalUntil = now + FRONTAL_PRESSURE_TICKS
		state.retreatRetryAt = Long.MIN_VALUE
		applyFrontalPressure(mob, target)
	}

	/** 按兵种体型确定绕行距离，并拒绝只能接近墙壁的部分路径。 */
	private fun findFlankPath(mob: Mob, target: LivingEntity): Path? {
		val radius = when (mob) {
			is AbstractSkeleton -> maxOf(UltraHardConfigs.values.skeletonMinimumDistance + 1.0, UltraHardConfigs.values.skeletonRetreatDistance)
			is Pillager -> 8.0
			is Evoker -> 7.0
			is Witch -> UltraHardConfigs.values.witchBacklineDistance
			is Ravager -> 4.0
			is Silverfish, is Endermite -> 2.5
			else -> 3.0
		}
		val facing = AiSupport.shieldFacing(target)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val relative = mob.position().subtract(target.position())
		val side = if (relative.dot(left) >= 0.0) 1.0 else -1.0
		for (direction in listOf(side, -side)) {
			val waypoint = if (isBowSkeleton(mob) || AiSupport.horizontalDirection(relative).dot(facing) > 0.25) {
				left.scale(direction * radius)
			} else facing.scale(-radius).add(left.scale(direction * radius * 0.5))
			val destination = target.position().add(waypoint)
			val offset = destination.subtract(mob.position())
			if (!mob.level().noCollision(mob, mob.boundingBox.move(offset))) continue
			// 骷髅侧方射击点必须有站立面且射界通畅，狭窄地形交回正面攻击。
			if (isBowSkeleton(mob)) {
				val floor = BlockPos.containing(destination).below()
				if (!mob.level().getBlockState(floor).isFaceSturdy(mob.level(), floor, Direction.UP)) continue
				val eye = destination.add(0.0, mob.eyeHeight.toDouble(), 0.0)
				val sight = mob.level().clip(ClipContext(eye, target.eyePosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob))
				if (sight.type != HitResult.Type.MISS) continue
			}
			val path = mob.navigation.createPath(BlockPos.containing(destination), 0)
			if (path != null && path.canReach()) return path
		}
		return null
	}

	/** 只使用已经确认可达的路径；检查与执行共用按兵种计算的半径。 */
	@JvmStatic
	fun flankShield(mob: Mob, target: LivingEntity, speed: Double) {
		if (!shouldFlankShield(mob, target)) return
		val state = flankStates[mob] ?: return
		if (!mob.navigation.moveTo(state.path, speed)) beginFrontalPressure(mob, target, state, mob.level().gameTime)
	}

	fun shouldCancelShieldedMelee(mob: Mob, target: Entity): Boolean =
		target is LivingEntity && shouldFlankShield(mob, target)

	@JvmStatic
	fun retreatSkeleton(skeleton: AbstractSkeleton, target: LivingEntity) {
		if (skeleton is WitherSkeleton && !isBowSkeleton(skeleton)) return
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
		flankShield(skeleton, target, config.skeletonRetreatSpeed)
	}

	/** 返回是否已接管当次移动，供不同兵种避免用绕后或盲目后退覆盖正面施压。 */
	internal fun maintainFrontalPressure(mob: Mob, target: LivingEntity): Boolean {
		if (!isFrontalPressureActive(mob)) return false
		applyFrontalPressure(mob, target)
		return true
	}

	/** 近战直接逼近；远程在有效距离内停留射击，退路受阻时原地进攻。 */
	private fun applyFrontalPressure(mob: Mob, target: LivingEntity) {
		val state = flankStates[mob] ?: return
		val now = mob.level().gameTime
		if (state.pressureUpdatedAt == now) return
		state.pressureUpdatedAt = now
		if (!AiSupport.isRangedCombatant(mob)) {
			mob.navigation.moveTo(target, 1.2)
			return
		}
		val config = UltraHardConfigs.values
		val minimum = when (mob) {
			is AbstractSkeleton -> config.skeletonMinimumDistance
			is Witch -> config.witchBacklineDistance
			is Evoker -> 7.0
			else -> 6.0
		}
		val distance = mob.distanceToSqr(target)
		if (distance > (minimum + 4.0) * (minimum + 4.0) || !mob.sensing.hasLineOfSight(target)) {
			mob.navigation.moveTo(target, 1.1)
			return
		}
		if (distance < minimum * minimum) {
			// 可用退路短期复用；无路可退后两秒再检查，避免墙角持续重复寻路。
			if (now < state.retreatRetryAt) return
			state.retreatRetryAt = now + 40L
			val away = AiSupport.horizontalDirection(mob.position().subtract(target.position()))
			val left = Vec3(-away.z, 0.0, away.x)
			for (direction in listOf(away, away.add(left).normalize(), away.subtract(left).normalize())) {
				val destination = mob.position().add(direction.scale(3.0))
				if (!mob.level().noCollision(mob, mob.boundingBox.move(destination.subtract(mob.position())))) continue
				val path = mob.navigation.createPath(BlockPos.containing(destination), 0)
				if (path != null && path.canReach() && mob.navigation.moveTo(path, 1.1)) {
					state.retreatRetryAt = now + 20L
					return
				}
			}
		}
		// 只停止移动，不取消原版弓弩蓄力、药水投掷或施法。
		mob.navigation.stop()
		mob.lookControl.setLookAt(target, 30.0f, 30.0f)
	}
}
