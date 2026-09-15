package com.nanagokyuu.ultrahard.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When death-protection (totem) effects successfully trigger, always remove the
 * protecting item from both hands — including edge cases where shrink/consume
 * did not clear the stack.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemMixin {
	@Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
	private void ultrahard$consumeTotemWhenTriggered(
			DamageSource source,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValueZ()) {
			return;
		}

		LivingEntity self = (LivingEntity) (Object) this;
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = self.getItemInHand(hand);
			if (isDeathProtectionItem(stack)) {
				self.setItemInHand(hand, ItemStack.EMPTY);
			}
		}
	}

	private static boolean isDeathProtectionItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.has(DataComponents.DEATH_PROTECTION) || stack.is(Items.TOTEM_OF_UNDYING);
	}
}
