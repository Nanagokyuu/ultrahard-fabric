package com.nanagokyuu.ultrahard.ai.combat

import com.nanagokyuu.ultrahard.ai.AiSupport
import com.nanagokyuu.ultrahard.ai.WorldAi
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import java.lang.ref.WeakReference
import java.util.EnumSet
import java.util.UUID
import java.util.WeakHashMap
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.Vec3

/** 同一玩家周围混编：僵尸负责前后，持弓骷髅负责左右；不强迫狭窄地形使用编队。 */
internal object MixedUndeadFormation {
	/** 缓存共享朝向和空间检查结果；成员使用弱引用，卸载后不会被编队长期保留。 */
	private data class Group(val tick: Long, val origin: Vec3, val facing: Vec3, val members: List<WeakReference<Mob>>, val spacious: Boolean)
	private val groups = WeakHashMap<LivingEntity, Group>()

	fun register(zombie: Zombie) {
		zombie.goalSelector.addGoal(1, MixedZombieGoal(zombie))
	}

	/** 所有持弓骷髅共用同一兵种判定，包括生成时或后来获得弓的凋灵骷髅。 */
	fun isArcher(mob: Mob): Boolean = AiSupport.isBowSkeleton(mob)

	fun direction(mob: Mob, target: LivingEntity): Vec3? {
		val level = AiSupport.ultraHardLevel(mob) ?: return null
		if (target !is Player || !target.isAlive || target.isCreative || target.isSpectator) return null
		if (mob !is Zombie && !isArcher(mob)) return null
		val now = level.gameTime
		var group = groups[target]
		// 每秒刷新一次；玩家移动超过两格时提前刷新，避免继续使用远处的空间检查结果。
		if (group == null || now - group.tick >= 20L || now < group.tick || target.position().distanceToSqr(group.origin) > 4.0) {
			val members = level.getEntities(null, target.boundingBox.inflate(24.0)) {
				it is Mob && eligible(it, target) && (it is Zombie || isArcher(it))
			}.filterIsInstance<Mob>().sortedBy { it.uuid }
			val mixed = members.any { it is Zombie } && members.any(::isArcher)
			// 两种兵种共享同一朝向，避免分别采样玩家转头后得到交叉的阵形。
			val tallest = members.maxByOrNull { it.bbHeight } ?: mob
			group = Group(now, target.position(), group?.facing ?: AiSupport.shieldFacing(target),
				members.map { WeakReference(it) }, mixed && UndeadMovement.hasManeuveringRoom(tallest, target.position()))
			groups[target] = group
		}
		if (!group.spacious) return null
		// 即使快照尚未过期，也实时过滤死亡、离队及换武器成员，防止剑士占用弓手侧翼。
		val members = group.members.mapNotNull { it.get() }.filter { eligible(it, target) }
		if (members.none { it is Zombie } || members.none(::isArcher)) return null
		val peers = members.filter { if (mob is Zombie) it is Zombie else isArcher(it) }
		val index = peers.indexOf(mob)
		if (index < 0) return null
		val facing = group.facing
		val left = Vec3(-facing.z, 0.0, facing.x)
		val axis = if (mob is Zombie) facing else left
		val tangent = if (mob is Zombie) left else facing
		val side = if (index % 2 == 0) 1.0 else -1.0
		val countOnSide = (peers.size + 1 - index % 2) / 2
		// 同一侧人数较多时在小扇区内分散，不跨入另一兵种的主攻方向。
		val spread = if (countOnSide <= 1) 0.0 else (index / 2).toDouble() / (countOnSide - 1) - 0.5
		return axis.scale(side).add(tangent.scale(spread * 0.8)).normalize()
	}

	private fun eligible(mob: Mob, target: LivingEntity): Boolean = mob.isAlive && mob.target === target &&
		mob.level() === target.level() && mob.distanceToSqr(target) <= 24.0 * 24.0 && !WorldAi.isSheltering(mob)

	/** 移动期间暂时接管僵尸近战导航，到位后及时释放，让原版挥击正常发生。 */
	private class MixedZombieGoal(private val zombie: Zombie) : Goal() {
		private var targetId: UUID? = null
		private var path: Path? = null
		private var destination: Vec3? = null
		private var retryAt = Long.MIN_VALUE
		/** 单轮换位最多四秒；路径每十刻重算，而不是每只僵尸每刻重复寻路。 */
		private var deadline = 0L
		private var repathAt = 0L

		init {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
		}

		private fun desiredPoint(): Vec3? {
			val target = zombie.target ?: return null
			val direction = direction(zombie, target) ?: return null
			return target.position().add(direction.scale(UltraHardConfigs.values.zombieFrontDistance))
		}

		override fun canUse(): Boolean {
			val now = zombie.level().gameTime
			val target = zombie.target ?: return false
			if (targetId != target.uuid) {
				targetId = target.uuid
				retryAt = Long.MIN_VALUE
			}
			if (now < retryAt) return false
			retryAt = now + 20L
			val point = desiredPoint() ?: return false
			if (zombie.position().distanceToSqr(point) <= 1.0) return false
			// 只有已经得到完整可达路径才占用 MOVE，失败时让原版近战继续运行。
			path = UndeadMovement.pathTo(zombie, point) ?: return false
			destination = point
			return true
		}

		override fun start() {
			deadline = zombie.level().gameTime + 80L
			repathAt = zombie.level().gameTime + 10L
			if (!zombie.navigation.moveTo(path, UltraHardConfigs.values.zombieCoordinationSpeed)) destination = null
		}

		override fun canContinueToUse(): Boolean {
			val point = destination ?: return false
			return AiSupport.ultraHardLevel(zombie) != null && !WorldAi.isSheltering(zombie) &&
				zombie.target?.uuid == targetId && zombie.target?.isAlive == true && zombie.level().gameTime < deadline &&
				zombie.position().distanceToSqr(point) > 1.0
		}

		override fun requiresUpdateEveryTick(): Boolean = true

		override fun tick() {
			val target = zombie.target ?: return
			zombie.lookControl.setLookAt(target, 30.0f, 30.0f)
			val now = zombie.level().gameTime
			if (now < repathAt) return
			repathAt = now + 10L
			destination = desiredPoint()
			val point = destination ?: return
			path = UndeadMovement.pathTo(zombie, point)
			if (path == null || !zombie.navigation.moveTo(path, UltraHardConfigs.values.zombieCoordinationSpeed)) destination = null
		}

		override fun stop() {
			zombie.navigation.stop()
			destination = null
			path = null
			retryAt = zombie.level().gameTime + 40L
		}
	}
}
