package com.nanagokyuu.ultrahard.ai.combat

import com.nanagokyuu.ultrahard.ai.AiSupport
import java.lang.ref.WeakReference
import java.util.EnumSet
import java.util.UUID
import java.util.WeakHashMap
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 未持弓凋灵骷髅的独立近战战术；持弓时完全让出控制权给普通骷髅弓箭 AI。 */
internal object WitherSkeletonCombat {
	/** 只记录目标标识和换侧方向，不持有玩家实体，避免待执行动作阻止实体回收。 */
	private data class Turn(val targetId: UUID, val direction: Vec3)
	private data class Group(val tick: Long, val facing: Vec3, val members: List<WeakReference<WitherSkeleton>>)
	private val turns = WeakHashMap<WitherSkeleton, Turn>()
	private val groups = WeakHashMap<LivingEntity, Group>()

	fun register(skeleton: WitherSkeleton) {
		// 换位期间占用移动与朝向，防止原版近战寻路每刻覆盖绕行路径。
		skeleton.goalSelector.addGoal(1, RepositionGoal(skeleton))
	}

	fun afterAttack(skeleton: WitherSkeleton, target: Entity) {
		// 中途换弓后不再产生近战换位请求；原版装备变更回调负责切换弓箭目标。
		if (AiSupport.isBowSkeleton(skeleton)) return
		if (AiSupport.ultraHardLevel(skeleton) == null || target !is Player || !target.isAlive) return
		val relative = AiSupport.horizontalDirection(skeleton.position().subtract(target.position()))
		// 命中、盾牌格挡或其他未造成伤害的真实出手都换侧，不依赖伤害成功标记。
		turns[skeleton] = Turn(target.uuid, Vec3(-relative.z, 0.0, relative.x))
	}

	private fun groupDirection(skeleton: WitherSkeleton, target: LivingEntity): Vec3? {
		val now = skeleton.level().gameTime
		var group = groups[target]
		if (group == null || now < group.tick || now - group.tick >= 20L) {
			// 弓手参加 SkeletonFormation 的远程包围，不占用近身包围中的角度名额。
			val members = skeleton.level().getEntities(null, target.boundingBox.inflate(24.0)) {
				it is WitherSkeleton && !AiSupport.isBowSkeleton(it) && it.isAlive && it.target === target
			}.filterIsInstance<WitherSkeleton>().sortedBy { it.uuid }
			group = Group(now, group?.facing ?: AiSupport.shieldFacing(target), members.map { WeakReference(it) })
			groups[target] = group
		}
		val members = group.members.mapNotNull { it.get() }.filter {
			!AiSupport.isBowSkeleton(it) && it.isAlive && it.target === target &&
				it.level() === skeleton.level() && it.distanceToSqr(target) <= 24.0 * 24.0
		}
		val index = members.indexOf(skeleton)
		if (members.size < 2 || index < 0) return null
		val angle = index * 2.0 * PI / members.size
		val facing = group.facing
		return facing.scale(cos(angle)).add(Vec3(-facing.z, 0.0, facing.x).scale(sin(angle)))
	}

	private class RepositionGoal(private val skeleton: WitherSkeleton) : Goal() {
		private var targetId: UUID? = null
		private var direction: Vec3? = null
		private var path: Path? = null
		private var retryAt = Long.MIN_VALUE
		private var startedAt = 0L
		private var lastProgressAt = 0L
		/** 用接近分配位置的距离判断进展；玩家转头不会被误认为怪物已经完成换位。 */
		private var bestDistance = Double.MAX_VALUE
		private var repathAt = 0L

		init {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
		}

		private fun target(): Player? {
			// canUse、canContinueToUse 和 tick 共用此检查，拿起弓后正在运行的近战任务也会退出。
			if (AiSupport.isBowSkeleton(skeleton)) return null
			if (AiSupport.ultraHardLevel(skeleton) == null) return null
			return (skeleton.target as? Player)?.takeIf { it.isAlive && !it.isCreative && !it.isSpectator }
		}

		override fun canUse(): Boolean {
			val target = target() ?: run { turns.remove(skeleton); return false }
			val now = skeleton.level().gameTime
			if (targetId != target.uuid) {
				targetId = target.uuid
				retryAt = Long.MIN_VALUE
			}
			val turn = turns.remove(skeleton)?.takeIf { it.targetId == target.uuid }
			// 新攻击可以提前发起换位；没有新攻击时用重试间隔限制寻路开销。
			if (turn == null && now < retryAt) return false
			retryAt = now + 20L
			val desired = groupDirection(skeleton, target) ?: turn?.direction ?: return false
			if (!UndeadMovement.hasManeuveringRoom(skeleton, target.position())) return false
			direction = desired
			val destination = target.position().add(desired.scale(2.5))
			if (skeleton.position().distanceToSqr(destination) < 0.64) return false
			path = UndeadMovement.pathTo(skeleton, destination) ?: return false
			return true
		}

		override fun start() {
			// 所有超时使用世界游戏刻，服务器暂停不会让怪物瞬间耗尽换位时间。
			startedAt = skeleton.level().gameTime
			lastProgressAt = startedAt
			bestDistance = Double.MAX_VALUE
			repathAt = startedAt + 10L
			if (!skeleton.navigation.moveTo(path, 1.2)) direction = null
		}

		override fun requiresUpdateEveryTick(): Boolean = true

		override fun canContinueToUse(): Boolean {
			val target = target() ?: return false
			val desired = direction ?: return false
			if (target.uuid != targetId) return false
			val now = skeleton.level().gameTime
			val distance = skeleton.position().distanceToSqr(target.position().add(desired.scale(2.5)))
			if (distance < bestDistance - 0.25) {
				bestDistance = distance
				lastProgressAt = now
			}
			// 到位或绕行受阻后交回原版近战攻击，绝不无限绕圈而停止出手。
			return distance > 0.64 && now - lastProgressAt < 40L && now - startedAt < 80L
		}

		override fun tick() {
			val target = target() ?: return
			val desired = direction ?: return
			skeleton.lookControl.setLookAt(target, 30.0f, 30.0f)
			val now = skeleton.level().gameTime
			if (now < repathAt) return
			repathAt = now + 10L
			if (!UndeadMovement.hasManeuveringRoom(skeleton, target.position())) {
				direction = null
				return
			}
			path = UndeadMovement.pathTo(skeleton, target.position().add(desired.scale(2.5)))
			if (path == null || !skeleton.navigation.moveTo(path, 1.2)) direction = null
		}

		override fun stop() {
			// 主动释放路径与移动控制，原版弓箭或近战任务才能接管下一步动作。
			skeleton.navigation.stop()
			direction = null
			path = null
			// 留出接近与挥剑的时间；新的攻击通知可以立即打破这个等待窗口。
			retryAt = skeleton.level().gameTime + 40L
		}
	}
}
