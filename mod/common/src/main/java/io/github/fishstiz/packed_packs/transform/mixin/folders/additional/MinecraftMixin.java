package io.github.fishstiz.packed_packs.transform.mixin.folders.additional;

import io.github.fishstiz.packed_packs.Folders;
import io.github.fishstiz.packed_packs.gui.screens.PackedPacksScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "openScreen", at = @At("HEAD"), argsOnly = true)
    private Screen packed_packs$replacePackScreen(Screen screen) {
        return PackedPacksScreen.replace(screen);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void packed_packs$noteFocusLoss(CallbackInfo ci) {
        if (!Display.isActive()) Folders.expire();
    }

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void packed_packs$readMcmetaFromDisk(CallbackInfo ci) {
        Folders.stopServing();
    }
}
