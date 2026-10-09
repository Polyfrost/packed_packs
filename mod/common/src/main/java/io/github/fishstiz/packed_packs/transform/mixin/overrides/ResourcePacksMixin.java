package io.github.fishstiz.packed_packs.transform.mixin.overrides;

import io.github.fishstiz.packed_packs.Folders;
import io.github.fishstiz.packed_packs.Overrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.pack.ResourcePacks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.List;

@Mixin(ResourcePacks.class)
public abstract class ResourcePacksMixin {
    @Shadow
    @Final
    private File directory;
    @Shadow
    private List<ResourcePacks.UnopenedPack> availablePacks;
    @Shadow
    private List<ResourcePacks.UnopenedPack> selectedPacks;
    @Unique
    private boolean packed_packs$loaded;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void packed_packs$enforceOverridesOnLoad(CallbackInfo ci) {
        Overrides.enforce(this.selectedPacks, this.availablePacks);
    }

    @ModifyVariable(method = "setSelectedPacks", at = @At("HEAD"), argsOnly = true)
    private List<ResourcePacks.UnopenedPack> packed_packs$enforceOverrides(List<ResourcePacks.UnopenedPack> packs) {
        return Overrides.enforce(packs, this.availablePacks);
    }

    @Inject(method = "discoverAvailablePacks", at = @At("RETURN"), cancellable = true)
    private void packed_packs$discoverFolders(CallbackInfoReturnable<List<File>> cir) {
        cir.setReturnValue(Folders.discover(this.directory, cir.getReturnValue()));
    }

    @Inject(method = "reload", at = @At("TAIL"))
    private void packed_packs$applyDefaults(CallbackInfo ci) {
        if (!this.packed_packs$loaded) {
            this.packed_packs$loaded = true;
            Folders.onFirstReload(this.availablePacks, Minecraft.getInstance().options);
        }
    }
}
