package com.nanagokyuu.ultrahard

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.enchantment.Enchantment

object UltraHardEnchantments {
	@JvmField
	val LIFESTEAL: ResourceKey<Enchantment> =
		ResourceKey.create(Registries.ENCHANTMENT, UltraHardMod.id("lifesteal"))
}
