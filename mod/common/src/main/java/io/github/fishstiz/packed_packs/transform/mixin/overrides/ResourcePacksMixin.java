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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
    @Unique
    private Map<Object, Integer> packed_packs$index = Map.of();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void packed_packs$enforceOverridesOnLoad(CallbackInfo ci) {
        Overrides.enforce(this.selectedPacks, this.availablePacks);
    }

    @ModifyVariable(method = "setSelectedPacks", at = @At("HEAD"), argsOnly = true)
    private List<ResourcePacks.UnopenedPack> packed_packs$enforceOverrides(List<ResourcePacks.UnopenedPack> packs) {
        return Overrides.enforce(packs, this.availablePacks);
    }

    @Inject(method = "discoverAvailablePacks", at = @At("HEAD"), cancellable = true)
    private void packed_packs$discoverFolders(CallbackInfoReturnable<List<File>> cir) {
        List<File> files = Folders.discover(this.directory);
        Folders.preload(files);
        cir.setReturnValue(files);
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void packed_packs$indexPacks(CallbackInfo ci) {
        Map<Object, Integer> index = new HashMap<>();
        for (int i = 0; i < this.availablePacks.size(); i++) index.putIfAbsent(this.availablePacks.get(i), i);
        this.packed_packs$index = index;
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private boolean packed_packs$containsIndexed(List<?> packs, Object pack) {
        return this.packed_packs$index.containsKey(pack);
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"))
    private int packed_packs$indexOfIndexed(List<?> packs, Object pack) {
        return this.packed_packs$index.getOrDefault(pack, -1);
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Ljava/util/List;removeAll(Ljava/util/Collection;)Z"))
    private boolean packed_packs$removeAllHashed(List<?> packs, Collection<?> kept) {
        return packs.removeAll(new HashSet<>(kept));
    }

    @Inject(method = "reload", at = @At("TAIL"))
    private void packed_packs$applyDefaults(CallbackInfo ci) {
        this.packed_packs$index = Map.of();
        if (!this.packed_packs$loaded) {
            this.packed_packs$loaded = true;
            Folders.onFirstReload(this.availablePacks, Minecraft.getInstance().options);
        }
    }
}
