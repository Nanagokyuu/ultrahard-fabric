package com.nanagokyuu.ultrahard.events

import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import java.util.UUID
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/** 管理超困难的睡眠治疗，不再干预原版跳夜判定。 */
internal object UltraHardSleep {
	private data class SleepState(
		/** 以主世界昼夜日期作为每日治疗的唯一标识。 */
		val calendarDay: Long,
		var healingReported: Boolean = false,
	)

	private val sleepStates = HashMap<UUID, SleepState>()

	/** 只在首次检测到睡眠时记录日期；离床便丢弃快照，再次入睡重新建立。 */
	fun updateSleepState(player: ServerPlayer) {
		if (!player.isSleeping) {
			sleepStates.remove(player.uuid)
			return
		}
		val day = Math.floorDiv(player.level().overworldClockTime, TICKS_PER_DAY)
		if (sleepStates[player.uuid]?.calendarDay == day) return
		sleepStates[player.uuid] = SleepState(day)
		player.sendOverlayMessage(Component.literal("已入睡，连续睡满 5 秒后结算今日睡眠治疗。"))
	}

	/** 睡满五秒后每日治疗一次，治疗量按最大生命值比例并受上下限约束。 */
	@JvmStatic
	fun healSleepingPlayer(player: ServerPlayer) {
		if (!UltraHardDifficulties.isUltraHard(player.level()) || !player.isAlive
			|| !player.isSleeping || player.sleepTimer < SLEEP_HEALING_DELAY_TICKS) return
		val sleepState = sleepStates[player.uuid] ?: return
		val state = player as UltraHardPlayerState
		val day = sleepState.calendarDay
		if (state.ultrahardGetLastSleepHealingDay() == day) return

		// 在原版推进时间之前记录入睡日期，避免本次治疗占用次日额度。
		state.ultrahardSetLastSleepHealingDay(day)
		val config = UltraHardConfigs.values
		val healing = (player.maxHealth * config.sleepHealingFraction)
			.coerceIn(config.sleepHealingMinimum, config.sleepHealingMaximum)
		player.heal(healing)
		if (!sleepState.healingReported) {
			sleepState.healingReported = true
			player.sendOverlayMessage(Component.literal("睡眠治疗完成：恢复 %.1f 点生命值。".format(healing)))
		}
	}

	/** 难度切换后清除内存中的睡眠快照，防止旧难度状态带回超困难。 */
	fun clear(player: ServerPlayer) {
		sleepStates.remove(player.uuid)
	}

	/** 玩家离线后清除不需要持久化的睡眠快照。 */
	fun clearOffline(playerIds: Set<UUID>) {
		sleepStates.keys.removeIf { it !in playerIds }
	}

	private const val TICKS_PER_DAY = 24_000L
	private const val SLEEP_HEALING_DELAY_TICKS = 100
}
