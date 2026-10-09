package io.github.fishstiz.packed_packs.transform.mixin.folders;

import io.github.fishstiz.packed_packs.Folders;
import net.minecraft.client.resource.pack.CustomResourcePack;
import net.minecraft.client.resource.pack.DirectoryResourcePack;
import net.minecraft.client.resource.pack.ZippedResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

@Mixin({ZippedResourcePack.class, DirectoryResourcePack.class})
public abstract class PackMcmetaMixin extends CustomResourcePack {
    private PackMcmetaMixin(File file) {
        super(file);
    }

    @Inject(method = "openResource", at = @At("HEAD"), cancellable = true)
    private void packed_packs$cachedMcmeta(String path, CallbackInfoReturnable<InputStream> cir) {
        if (!"pack.mcmeta".equals(path)) return;
        byte[] mcmeta = Folders.mcmeta(this.file);
        if (mcmeta != null) cir.setReturnValue(new ByteArrayInputStream(mcmeta));
    }
}
