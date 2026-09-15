package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyReturnValue;

@Mixin(ItemStack.class)
public abstract class ShieldDurabilityMixin {
	@ModifyReturnValue(method = "getMaxDamage", at = @At("RETURN"))
	private int ultrahard$shieldDurability(int original) {
		ItemStack self = (ItemStack) (Object) this;
		return self.getItem() instanceof ShieldItem
				? UltraHardDifficulties.SHIELD_MAX_DURABILITY
				: original;
	}
}
