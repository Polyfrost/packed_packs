package io.github.fishstiz.packed_packs.transform.mixin.folders;

import io.github.fishstiz.packed_packs.Folders;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;

@Mixin(ResourcePacks.UnopenedPack.class)
public abstract class UnopenedPackMixin {
    @Shadow
    @Final
    private File file;
    @Shadow
    private BufferedImage icon;
    @Shadow
    private Identifier iconLocation;
    @Unique
    private Folders.Stat packed_packs$stat;
    @Unique
    private String packed_packs$key;
    @Unique
    private CompletableFuture<BufferedImage> packed_packs$iconTask;

    @Unique
    private Folders.Stat packed_packs$stat() {
        if (this.packed_packs$stat == null) {
            this.packed_packs$stat = Folders.stat(this.file);
        }
        return this.packed_packs$stat;
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void packed_packs$nameByPath(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(this.packed_packs$stat().id());
    }

    @Inject(method = "toString", at = @At("HEAD"), cancellable = true)
    private void packed_packs$compareByPath(CallbackInfoReturnable<String> cir) {
        if (this.packed_packs$key == null) this.packed_packs$key = this.packed_packs$stat().key();
        cir.setReturnValue(this.packed_packs$key);
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/pack/ResourcePack;getIcon()Ljava/awt/image/BufferedImage;"))
    private BufferedImage packed_packs$deferIcon(ResourcePack pack) {
        return null;
    }

    @Inject(method = "bindIcon", at = @At("HEAD"), cancellable = true)
    private void packed_packs$loadIcon(TextureManager manager, CallbackInfo ci) {
        if (this.iconLocation != null || this.icon != null) return;
        if (this.packed_packs$iconTask == null) {
            File file = this.file;
            this.packed_packs$iconTask = CompletableFuture.supplyAsync(() -> Folders.readIcon(file)).exceptionally(error -> null);
        }
        this.icon = this.packed_packs$iconTask.getNow(null);
        if (this.icon != null) return;
        Identifier fallback = Folders.defaultIcon(manager);
        if (this.packed_packs$iconTask.isDone()) this.iconLocation = fallback;
        manager.bind(fallback);
        ci.cancel();
    }
}
