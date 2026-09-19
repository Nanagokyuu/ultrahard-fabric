package com.nanagokyuu.ultrahard.events

import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import com.nanagokyuu.ultrahard.equipment.UltraHardEquipment
import java.util.UUID
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.GameType

/** 负责当前生命击杀计数、死亡总结和模式切换清零；计数本身由玩家存档持久化。 */
internal object UltraHardKillStreak {
	/** 记录上一帧的游戏模式，用于检测进入创造或旁观并清空当前生命击杀数。 */
	private val previousGameModes = HashMap<UUID, GameType>()

	fun register() {
		// 生物死亡后再统计击杀，确保只有真正死亡的敌对生物才会增加连击数。
		ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
			if (entity is ServerPlayer) {
				val wasUltraHard = UltraHardDifficulties.isUltraHard(entity.level())
				val kills = lifeKills(entity)
				val correction = (1.0f - UltraHardEquipment.killDamageMultiplier(entity)) * 100.0f
				resetLifeKills(
					entity,
					if (wasUltraHard) "本次生命结束：击杀 ${kills} 个敌对生物，敌人伤害修正 -${formatPercent(correction)}%，已清零。" else null,
				)
				return@register
			}
			if (entity.level() !is ServerLevel || !UltraHardDifficulties.isUltraHard(entity.level())) return@register
			if (!isHostile(entity)) return@register
			val killer = resolvePlayerKiller(source) ?: return@register
			if (killer.isCreative || killer.isSpectator) return@register
			addLifeKill(killer)
		}

	}

	/** 返回玩家当前生命累计的有效击杀数。 */
	private fun lifeKills(player: ServerPlayer): Int =
		(player as UltraHardPlayerState).ultrahardGetLifeKills()

	/** 增加击杀数，并在每达到一个十击杀节点时显示正反馈。 */
	private fun addLifeKill(player: ServerPlayer) {
		val state = player as UltraHardPlayerState
		val oldKills = state.ultrahardGetLifeKills()
		val newKills = oldKills + 1
		state.ultrahardSetLifeKills(newKills)
		val interval = UltraHardConfigs.values.killDamageMilestoneInterval
		if (newKills / interval <= oldKills / interval) return

		val multiplier = UltraHardEquipment.killDamageMultiplier(player)
		val correction = (1.0f - multiplier) * 100.0f
		// 粒子和 Action Bar 都只在十击杀节点触发，不污染聊天框。
		player.level().sendParticles(
			ParticleTypes.HAPPY_VILLAGER,
			player.x,
			player.y + player.bbHeight * 0.5,
			player.z,
			12,
			0.35,
			0.5,
			0.35,
			0.05,
		)
		player.sendOverlayMessage(
			Component.literal("战斗连击：${newKills}｜敌人伤害修正：-${formatPercent(correction)}%"),
		)
	}

	/** 清空当前生命击杀数，并在死亡时保留一条总结提示。 */
	private fun resetLifeKills(player: ServerPlayer, message: String? = null) {
		val state = player as UltraHardPlayerState
		state.ultrahardSetLifeKills(0)
		if (message != null) player.sendSystemMessage(Component.literal(message))
	}

	/** 只允许玩家本人或玩家发射的投射物获得击杀计数。 */
	private fun resolvePlayerKiller(source: net.minecraft.world.damagesource.DamageSource): ServerPlayer? {
		(source.entity as? ServerPlayer)?.let { return it }
		val projectile = source.directEntity as? Projectile ?: return null
		return projectile.owner as? ServerPlayer
	}

	/** 进入创造或旁观时立即清空连击，避免通过指令保留战斗收益。 */
	fun trackGameMode(player: ServerPlayer) {
		val mode = player.gameMode()
		val previous = previousGameModes.put(player.uuid, mode)
		if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
			if (previous != mode || lifeKills(player) > 0) resetLifeKills(player)
		}
	}

	private fun formatPercent(value: Float): String = "%.0f".format(value)

	/** 离开超困难时清空击杀收益；不额外发送聊天消息。 */
	fun clear(player: ServerPlayer) {
		if (lifeKills(player) > 0) resetLifeKills(player)
	}

	/** 离线玩家只清理模式快照，不清理存档中的击杀数。 */
	fun clearOffline(playerIds: Set<UUID>) {
		previousGameModes.keys.removeIf { it !in playerIds }
	}

	private fun isHostile(entity: Entity?): Boolean = entity is Enemy || entity is Monster
}
