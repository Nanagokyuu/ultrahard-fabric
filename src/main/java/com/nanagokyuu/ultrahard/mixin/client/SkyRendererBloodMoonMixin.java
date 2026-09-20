package com.nanagokyuu.ultrahard.mixin.client;

import com.nanagokyuu.ultrahard.client.BloodMoonClient;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 将血月的月面动态颜色改为暗红色，太阳和星星仍使用原版渲染。 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererBloodMoonMixin {
	@ModifyArg(
			method = "renderMoon",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
			),
			index = 1
	)
	private Vector4f ultrahard$redMoon(Vector4f original) {
		return BloodMoonClient.isBloodMoon()
				? new Vector4f(1.0f, 0.08f, 0.08f, original.w())
				: original;
	}
}
