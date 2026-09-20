package com.nanagokyuu.ultrahard.client

import com.nanagokyuu.ultrahard.event.BloodMoon
import net.minecraft.client.Minecraft

/** 客户端只读取服务端同步的世界时钟，不自行推进血月状态。 */
object BloodMoonClient {
	@JvmStatic
	fun isBloodMoon(): Boolean = Minecraft.getInstance().level?.let(BloodMoon::isBloodMoon) == true
}
