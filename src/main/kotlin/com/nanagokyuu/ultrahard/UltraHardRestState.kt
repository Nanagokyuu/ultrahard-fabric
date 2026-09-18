package com.nanagokyuu.ultrahard

import com.mojang.serialization.Codec
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

/** 主世界共享的当晚休息锁，防止重伤玩家退出或换维度后由队友代为跳夜。 */
class UltraHardRestState(private var blockedDay: Long = Long.MIN_VALUE) : SavedData() {
	companion object {
		const val BLOCKED_MESSAGE = "有玩家重伤入睡，今夜将无法跳过。"
		private val TYPE = SavedDataType(
			UltraHardMod.id("rest_state"),
			::UltraHardRestState,
			Codec.LONG.fieldOf("blocked_day").xmap(::UltraHardRestState) { it.blockedDay }.codec(),
			// 使用通用命令存储的数据修复类别，自定义日期字段作为额外数据保留。
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE,
		)

		private fun state(level: ServerLevel): UltraHardRestState =
			level.server.overworld().dataStorage.computeIfAbsent(TYPE)

		/** 仅记录日期，次日自然失效；与玩家存档一起保存可覆盖正常重启。 */
		fun blockTonight(level: ServerLevel) {
			val state = state(level)
			val day = Math.floorDiv(level.overworldClockTime, 24_000L)
			if (state.blockedDay == day) return
			state.blockedDay = day
			state.setDirty()
			// 每晚首次锁定时全服广播一次，后来入睡者通过床边提示了解限制。
			level.server.playerList.broadcastSystemMessage(Component.literal(BLOCKED_MESSAGE), false)
		}

		@JvmStatic
		fun isBlocked(level: ServerLevel): Boolean =
			state(level).blockedDay == Math.floorDiv(level.overworldClockTime, 24_000L)
	}
}
