package com.nanagokyuu.ultrahard

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object UltraHardMod : ModInitializer {
	const val MOD_ID = "ultrahard"
	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		val ultra = UltraHardDifficulties.ULTRAHARD
		UltraHardEvents.register()
		LOGGER.info(
			"Ultra Hard initialized as Difficulty.{} (id={}, key={})",
			ultra.name,
			ultra.id,
			ultra.serializedName,
		)
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
