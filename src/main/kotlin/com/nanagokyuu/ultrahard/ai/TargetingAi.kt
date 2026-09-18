package com.nanagokyuu.ultrahard.ai

import com.nanagokyuu.ultrahard.UltraHardConfigs
import com.nanagokyuu.ultrahard.UltraHardEquipment
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.WeakHashMap
import java.lang.ref.WeakReference
import kotlin.math.sqrt

/** 每只怪物独立积累仇恨；弱引用键与短期缓存避免保留死亡实体或离线玩家。 */
internal object TargetingAi {
	private data class Threat(val player: WeakReference<ServerPlayer>, var amount: Double, var updatedAt: Long, var lastHitAt: Long)
	private data class Reachability(val origin: Vec3, val destination: Vec3, val expiresAt: Long, val reachable: Boolean)
	private data class TargetState(
		val threats: MutableMap<UUID, Threat> = HashMap(),
		val paths: MutableMap<UUID, Reachability> = HashMap(),
		var selectedId: UUID? = null,
		var selectedPlayer: WeakReference<ServerPlayer>? = null,
		var lockedUntil: Long = Long.MIN_VALUE,
	)
	private val states = WeakHashMap<Mob, TargetState>()

	/** 锁定期间拦截原版目标任务改选其它玩家；清空目标和女巫治疗友军仍可正常进行。 */
	fun shouldKeepSelectedTarget(mob: Mob, proposed: LivingEntity?): Boolean {
		val level = AiSupport.ultraHardLevel(mob) ?: return false
		if (proposed !is ServerPlayer) return false
		val state = states[mob] ?: return false
		val selected = state.selectedPlayer?.get() ?: return false
		val radius = UltraHardConfigs.values.targetScanRadius
		return proposed !== selected && level.gameTime < state.lockedUntil && selected.isAlive
			&& selected.level() === level && !selected.isCreative && !selected.isSpectator
			&& mob.distanceToSqr(selected) <= radius * radius && mob.canAttack(selected)
	}

	/** 只记录最终生效的玩家伤害；无效命中、被格挡和纯环境伤害不会制造仇恨。 */
	fun recordPlayerDamage(mob: Mob, player: ServerPlayer, damage: Float) {
		val level = AiSupport.ultraHardLevel(mob) ?: return
		if (!damage.isFinite() || damage <= 0.0f || !mob.isAlive || !AiSupport.isHostile(mob)) return
		val config = UltraHardConfigs.values
		val state = states.getOrPut(mob) { TargetState() }
		val now = level.gameTime
		val threat = state.threats[player.uuid]?.takeIf { it.player.get() === player }
			?: Threat(WeakReference(player), 0.0, now, now).also { state.threats[player.uuid] = it }
		decay(threat, now)
		threat.amount = (threat.amount + damage * config.threatPerDamage).coerceAtMost(config.threatMaximum)
		threat.lastHitAt = now
	}

	private fun decay(threat: Threat, now: Long) {
		val elapsed = (now - threat.updatedAt).coerceAtLeast(0L)
		threat.amount = (threat.amount - elapsed / 20.0 * UltraHardConfigs.values.threatDecayPerSecond).coerceAtLeast(0.0)
		threat.updatedAt = now
	}

	/** 当前目标有稳定性加分，最近贴身造成伤害的玩家有反击加分，重甲也能主动吸引火力。 */
	private fun priority(mob: Mob, player: ServerPlayer, state: TargetState, now: Long): Double {
		val config = UltraHardConfigs.values
		val distance = sqrt(mob.distanceToSqr(player))
		val threat = state.threats[player.uuid]
		val retaliation = if (distance <= 4.0 && threat != null && now - threat.lastHitAt <= 60L) 6.0 else 0.0
		val stability = if (player.uuid == state.selectedId) 2.0 else 0.0
		return (threat?.amount ?: 0.0) + retaliation + stability - UltraHardEquipment.defenseScore(player) * config.targetEquipmentPenalty - distance * config.targetDistancePenalty
	}

	/** 远程单位能直接射击可见目标；近战需要可达路径，失败结果缓存三秒后重试。 */
	private fun canEngage(mob: Mob, player: ServerPlayer, state: TargetState, now: Long): Boolean {
		if (AiSupport.isRangedCombatant(mob) && mob.sensing.hasLineOfSight(player)) return true
		if (mob.distanceToSqr(player) <= 4.0 && mob.sensing.hasLineOfSight(player)) return true
		val origin = mob.position()
		val destination = player.position()
		state.paths[player.uuid]?.takeIf {
			now < it.expiresAt && origin.distanceToSqr(it.origin) < 4.0 && destination.distanceToSqr(it.destination) < 4.0
		}?.let { return it.reachable }
		val path = mob.navigation.createPath(player, 0)
		val reachable = path != null && path.canReach()
		state.paths[player.uuid] = Reachability(origin, destination, now + if (reachable) 20L else 60L, reachable)
		return reachable
	}

	fun selectTarget(mob: Mob) {
		val level = AiSupport.ultraHardLevel(mob) ?: run { states.remove(mob); return }
		if (!AiSupport.isHostile(mob) || !AiSupport.shouldUpdate(mob)) return
		val state = states.getOrPut(mob) { TargetState() }
		val now = level.gameTime
		val config = UltraHardConfigs.values
		// 三十秒未攻击、离线、死亡或跨维度后移除记录；不会把上一条生命的仇恨带给复活者。
		state.threats.entries.removeIf { (uuid, threat) ->
			val player = level.server.playerList.getPlayer(uuid)
			decay(threat, now)
			player == null || threat.player.get() !== player || !player.isAlive || player.level() !== level || now - threat.lastHitAt > 600L
		}
		state.paths.entries.removeIf { now >= it.value.expiresAt }
		val radius = config.targetScanRadius
		val candidates = level.getEntities(mob, mob.boundingBox.inflate(radius)) { entity ->
			entity is ServerPlayer && entity.isAlive && !entity.isSpectator && !entity.isCreative
				&& mob.distanceToSqr(entity) <= radius * radius && mob.canAttack(entity)
				&& (entity.uuid == state.selectedId || entity === mob.target || mob.sensing.hasLineOfSight(entity))
		}.filterIsInstance<ServerPlayer>()
		val incumbent = candidates.firstOrNull { it === state.selectedPlayer?.get() }
			?.takeIf { canEngage(mob, it, state, now) }
		// 锁定期间恢复本模块选定的目标，不受原版报复目标的反复改写影响。
		if (incumbent != null && now < state.lockedUntil) {
			if (mob.target !== incumbent) mob.target = incumbent
			return
		}
		val ranked = candidates.sortedWith(compareByDescending<ServerPlayer> { priority(mob, it, state, now) }
			.thenBy { it.distanceToSqr(mob) }.thenBy { it.uuid })
		val best = ranked.firstOrNull { canEngage(mob, it, state, now) }
		val selected = if (incumbent != null && (best == null || priority(mob, best, state, now) < priority(mob, incumbent, state, now) + config.targetSwitchMargin)) incumbent else best
		if (selected == null) {
			state.selectedId = null
			state.selectedPlayer = null
			if (mob.target is ServerPlayer) mob.target = null
			return
		}
		if (selected !== state.selectedPlayer?.get()) {
			state.selectedId = selected.uuid
			state.selectedPlayer = WeakReference(selected)
			state.lockedUntil = now + config.targetLockTicks
		}
		if (mob.target !== selected) mob.target = selected
	}
}
