package com.nanagokyuu.ultrahard.network

import com.nanagokyuu.ultrahard.UltraHardMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** 服务端向客户端同步当前生命的战斗经验，供客户端实时绘制进度预览。 */
data class CombatExperiencePayload(val experience: Int, val maximum: Int, val active: Boolean) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<CombatExperiencePayload> = TYPE

	companion object {
		val TYPE: CustomPacketPayload.Type<CombatExperiencePayload> =
			// createType 内部会自动补 minecraft 命名空间，因此这里传入完整标识符会产生非法的 minecraft:ultrahard:...；直接构造 Type 保留本模组命名空间。
			CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(UltraHardMod.MOD_ID, "combat_experience"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, CombatExperiencePayload> =
			StreamCodec.composite(
				ByteBufCodecs.VAR_INT, CombatExperiencePayload::experience,
				ByteBufCodecs.VAR_INT, CombatExperiencePayload::maximum,
				ByteBufCodecs.BOOL, CombatExperiencePayload::active,
				::CombatExperiencePayload,
			)
	}
}
