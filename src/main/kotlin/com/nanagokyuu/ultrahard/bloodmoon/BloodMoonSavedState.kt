package com.nanagokyuu.ultrahard.bloodmoon

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.nanagokyuu.ultrahard.UltraHardMod
import java.util.UUID
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

/** 持久化血月的进行状态、玩家击杀统计和离线奖励。 */
class BloodMoonSavedState(
	var bloodMoonDay: Long = Long.MIN_VALUE,
	var started: Boolean = false,
	var endedNaturally: Boolean = false,
	var skipped: Boolean = false,
	initialKills: Map<UUID, Int> = emptyMap(),
	initialPendingRewards: Map<UUID, Int> = emptyMap(),
) : SavedData() {
	val kills: MutableMap<UUID, Int> = initialKills.toMutableMap()
	val pendingRewards: MutableMap<UUID, Int> = initialPendingRewards.toMutableMap()

	fun resetForDay(day: Long) {
		bloodMoonDay = day
		started = false
		endedNaturally = false
		skipped = false
		kills.clear()
		setDirty()
	}

	companion object {
		private val TYPE = SavedDataType(
			UltraHardMod.id("blood_moon"),
			::BloodMoonSavedState,
			RecordCodecBuilder.create { instance ->
				instance.group(
					Codec.LONG.fieldOf("bloodMoonDay").forGetter(BloodMoonSavedState::bloodMoonDay),
					Codec.BOOL.fieldOf("started").forGetter(BloodMoonSavedState::started),
					Codec.BOOL.fieldOf("endedNaturally").forGetter(BloodMoonSavedState::endedNaturally),
					Codec.BOOL.fieldOf("skipped").forGetter(BloodMoonSavedState::skipped),
					Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
						.fieldOf("kills")
						.forGetter { state -> state.kills.toMap() },
					Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
						.fieldOf("pendingRewards")
						.forGetter { state -> state.pendingRewards.toMap() },
				).apply(instance, ::BloodMoonSavedState)
			},
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE,
		)

		fun get(level: ServerLevel): BloodMoonSavedState =
			level.server.overworld().dataStorage.computeIfAbsent(TYPE)
	}
}
