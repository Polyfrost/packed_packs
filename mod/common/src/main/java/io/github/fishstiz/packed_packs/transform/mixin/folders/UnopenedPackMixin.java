package io.github.fishstiz.packed_packs.transform.mixin.folders;

import io.github.fishstiz.packed_packs.Folders;
import net.minecraft.client.resource.pack.ResourcePacks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(ResourcePacks.UnopenedPack.class)
public abstract class UnopenedPackMixin {
    @Shadow
    @Final
    private File file;
    @Unique
    private String packed_packs$id;

    @Unique
    private String packed_packs$id() {
        if (this.packed_packs$id == null) {
            this.packed_packs$id = Folders.id(this.file);
        }
        return this.packed_packs$id;
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void packed_packs$nameByPath(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(this.packed_packs$id());
    }

    @Inject(method = "toString", at = @At("HEAD"), cancellable = true)
    private void packed_packs$compareByPath(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(String.format("%s:%s:%d", this.packed_packs$id(), this.file.isDirectory() ? "folder" : "zip", this.file.lastModified()));
    }
}
