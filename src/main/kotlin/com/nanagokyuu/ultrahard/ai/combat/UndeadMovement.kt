package com.nanagokyuu.ultrahard.ai.combat

import com.nanagokyuu.ultrahard.ai.AiSupport
import com.nanagokyuu.ultrahard.ai.WorldAi
import java.util.WeakHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 亡灵共用的空间检查、游泳和退路越障；所有新增行为仅在超困难生效。 */
internal object UndeadMovement {
	/** 每只怪物独立计算后跳冷却，死亡或卸载后弱引用键会自动释放。 */
	private val jumpRetryAt = WeakHashMap<Mob, Long>()

	fun registerSwimming(skeleton: AbstractSkeleton) {
		// 记住原本的导航能力，退出超困难时不能把其他机制已有的浮游设置关掉。
		val originalCanFloat = skeleton.navigation.canFloat()
		val goal = object : FloatGoal(skeleton) {
			override fun canUse(): Boolean {
				val enabled = AiSupport.ultraHardLevel(skeleton) != null
				skeleton.navigation.setCanFloat(enabled || originalCanFloat)
				// 只增加水中浮游，不把免疫火焰的变种改成主动在岩浆中游泳。
				return enabled && skeleton.isInWater && super.canUse()
			}
		}
		// FloatGoal 构造器会修改导航标志，先恢复原值，再按当前难度动态启用。
		skeleton.navigation.setCanFloat(originalCanFloat)
		skeleton.goalSelector.addGoal(0, goal)
	}

	/** 搜索上下各一格的完整落脚点，拒绝悬空、低顶及未加载区域。 */
	fun standingPoint(mob: Mob, destination: Vec3): Vec3? {
		val level = mob.level()
		for (height in listOf(0, 1, -1)) {
			val block = BlockPos.containing(destination).offset(0, height, 0)
			if (!level.isLoaded(block) || !level.isLoaded(block.below())) continue
			val point = Vec3.atBottomCenterOf(block)
			if (!level.getBlockState(block.below()).isFaceSturdy(level, block.below(), Direction.UP)) continue
			if (!level.getFluidState(block).isEmpty) continue
			if (level.noCollision(mob, mob.boundingBox.move(point.subtract(mob.position())))) return point
		}
		return null
	}

	fun pathTo(mob: Mob, destination: Vec3): Path? {
		val point = standingPoint(mob, destination) ?: return null
		// createPath 可能只返回通向障碍物边缘的部分路径，这种结果不能用于战术换位。
		return mob.navigation.createPath(BlockPos.containing(point), 0)?.takeIf { it.canReach() }
	}

	/** 检查内外两圈八个方向，窄走廊、墙角和低顶不启用围攻换位。 */
	fun hasManeuveringRoom(mob: Mob, center: Vec3): Boolean {
		for (radius in listOf(1.5, 3.0)) {
			for (index in 0 until 8) {
				val angle = index * PI / 4.0
				if (standingPoint(mob, center.add(cos(angle) * radius, 0.0, sin(angle) * radius)) == null) return false
			}
		}
		return true
	}

	/** 后退受一格高台阶阻挡时做真实跳跃；先检查落脚面和完整上升、横移净空。 */
	fun tryBackwardJump(mob: Mob, retreating: Boolean = false) {
		if (mob !is Zombie && mob !is AbstractSkeleton) return
		val level = AiSupport.ultraHardLevel(mob) ?: return
		if (!mob.onGround() || mob.isPassenger || mob.isInWater || WorldAi.isSheltering(mob)) return
		val target = mob.target as? Player ?: return
		if (!target.isAlive || target.isCreative || target.isSpectator) return
		val now = level.gameTime
		if (now < (jumpRetryAt[mob] ?: Long.MIN_VALUE)) return
		val away = AiSupport.horizontalDirection(mob.position().subtract(target.position()))
		val movement = mob.deltaMovement
		val path = mob.navigation.path
		val yaw = Math.toRadians(mob.yRot.toDouble())
		val input = Vec3(mob.xxa * cos(yaw) - mob.zza * sin(yaw), 0.0, mob.zza * cos(yaw) + mob.xxa * sin(yaw))
		// 撞上台阶时实际速度可能归零，仍须读取导航和后退输入，不能只看速度。
		val backingUp = retreating || input.dot(away) > 0.01 || movement.dot(away) > 0.01 || (path != null && !path.isDone &&
			path.getNextEntityPos(mob).subtract(mob.position()).dot(away) > 0.1)
		if (!backingUp && mob.distanceToSqr(target) > 9.0) return
		val offset = away.scale(1.1)
		val step = BlockPos.containing(mob.position().add(offset))
		if (!level.isLoaded(step) || !level.getBlockState(step).isFaceSturdy(level, step, Direction.UP)) return
		val landing = mob.position().add(offset.x, step.y + 1.0 - mob.y, offset.z)
		// 只处理普通一格台阶；水面、两格高墙和过大高差交回原版导航。
		if (landing.y - mob.y !in 0.5..1.05 || !level.getFluidState(step.above()).isEmpty) return
		val box = mob.boundingBox
		// 分别验证垂直起跳区、空中的横移区和最终落点；凋灵骷髅按自身较高碰撞箱检查。
		if (!level.noCollision(mob, box.expandTowards(0.0, 1.25, 0.0))) return
		if (!level.noCollision(mob, box.move(0.0, 1.25, 0.0).expandTowards(offset.x, 0.0, offset.z))) return
		if (!level.noCollision(mob, box.move(landing.subtract(mob.position())))) return
		jumpRetryAt[mob] = now + 20L
		mob.jumpFromGround()
		// 保留原版跳跃高度和药水修正，只给后退方向冲量，不瞬移或修改方块。
		mob.deltaMovement = Vec3(away.x * 0.28, mob.deltaMovement.y, away.z * 0.28)
	}
}
