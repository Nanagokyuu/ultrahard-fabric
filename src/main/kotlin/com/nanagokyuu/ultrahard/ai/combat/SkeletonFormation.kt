package com.nanagokyuu.ultrahard.ai.combat

import com.nanagokyuu.ultrahard.ai.AiSupport
import com.nanagokyuu.ultrahard.ai.WorldAi
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 同一目标共享编队快照；弱引用防止死亡、卸载的骷髅被缓存保留。 */
internal object SkeletonFormation {
	private data class Group(val checkedAt: Long, val facing: Vec3, val members: List<WeakReference<AbstractSkeleton>>)
	private val groups = WeakHashMap<LivingEntity, Group>()

	fun direction(skeleton: AbstractSkeleton, target: LivingEntity): Vec3 {
		// 混编分工优先于纯弓手环形包围；持弓凋灵骷髅同样分配到左右翼。
		MixedUndeadFormation.direction(skeleton, target)?.let { return it }
		val level = skeleton.level()
		val now = level.gameTime
		var group = groups[target]
		// 同一玩家的所有弓手共用一秒快照，避免每只怪物每刻扫描周围实体。
		if (group == null || now < group.checkedAt || now - group.checkedAt >= 20L) {
			val members = level.getEntities(null, target.boundingBox.inflate(24.0)) {
				it is AbstractSkeleton && it.isAlive && it.target === target && !WorldAi.isSheltering(it)
					&& MixedUndeadFormation.isArcher(it)
			}.filterIsInstance<AbstractSkeleton>().sortedBy { it.uuid }
			// 编队朝向在交战期间固定，玩家转头不会使整圈骷髅不断追着旋转。
			val facing = group?.facing ?: AiSupport.shieldFacing(target)
			group = Group(now, facing, members.map { WeakReference(it) })
			groups[target] = group
		}
		val members = group.members.mapNotNull { it.get() }.filter {
			// 快照里的成员可能已死亡或换下弓，使用前必须再次检查当前状态。
			it.isAlive && it.target === target && it.level() === level
				&& !WorldAi.isSheltering(it) && MixedUndeadFormation.isArcher(it)
		}.let { if (skeleton in it) it else (it + skeleton).sortedBy { member -> member.uuid } }
		val index = members.indexOf(skeleton)
		// UUID 顺序稳定，使同一批成员不会因为查询返回顺序改变而互换站位。
		// 单只分别选择左侧、后方或右侧；多只均分整圈，四只时覆盖四个方向。
		val angle = if (members.size == 1) (1 + Math.floorMod(skeleton.uuid.hashCode(), 3)) * PI / 2.0
			else PI / 2.0 + index * 2.0 * PI / members.size
		val facing = group.facing
		val left = Vec3(-facing.z, 0.0, facing.x)
		// 以正前和左侧为水平基向量，将角度转换为目标周围的单位方向。
		return facing.scale(cos(angle)).add(left.scale(sin(angle)))
	}
}
