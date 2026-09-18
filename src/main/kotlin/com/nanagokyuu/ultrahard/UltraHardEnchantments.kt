package com.nanagokyuu.ultrahard

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.enchantment.Enchantment

/** 保存附魔注册键；具体附魔属性由数据资源定义，使用时从当前世界注册表解析。 */
object UltraHardEnchantments {
	@JvmField
	val LIFESTEAL: ResourceKey<Enchantment> =
		ResourceKey.create(Registries.ENCHANTMENT, UltraHardMod.id("lifesteal"))
}
