package com.nanagokyuu.ultrahard

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object UltraHardMod : ModInitializer {
	const val MOD_ID = "ultrahard"
	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		UltraHardGameRules.init()
		UltraHardCommands.register()
		UltraHardEvents.register()
		LOGGER.info("Ultra Hard (超困难) initialized")
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
