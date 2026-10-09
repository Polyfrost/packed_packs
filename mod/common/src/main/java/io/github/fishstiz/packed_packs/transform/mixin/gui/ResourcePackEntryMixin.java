package io.github.fishstiz.packed_packs.transform.mixin.gui;

import io.github.fishstiz.packed_packs.Overrides;
import net.minecraft.client.gui.screen.menu.resourcepacks.CustomResourcePackEntry;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePackEntry;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ResourcePackEntry.class)
public abstract class ResourcePackEntryMixin {
    @Shadow
    @Final
    protected ResourcePacksScreen parent;

    @Unique
    private static boolean packed_packs$isFixed(Object entry) {
        return entry instanceof CustomResourcePackEntry custom && Overrides.position(custom.getPack()) != null;
    }

    @Unique
    private boolean packed_packs$isFixed(int offset) {
        List<ResourcePackEntry> siblings = this.parent.getSiblingPacks((ResourcePackEntry) (Object) this);
        return packed_packs$isFixed(this) || packed_packs$isFixed(siblings.get(siblings.indexOf(this) + offset));
    }

    @Inject(method = "canMoveLeft", at = @At("RETURN"), cancellable = true)
    private void packed_packs$keepRequired(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof CustomResourcePackEntry custom && Overrides.isRequired(custom.getPack())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canMoveUp", at = @At("RETURN"), cancellable = true)
    private void packed_packs$keepFixedBelow(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && this.packed_packs$isFixed(-1)) cir.setReturnValue(false);
    }

    @Inject(method = "canMoveDown", at = @At("RETURN"), cancellable = true)
    private void packed_packs$keepFixedAbove(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && this.packed_packs$isFixed(1)) cir.setReturnValue(false);
    }
}
