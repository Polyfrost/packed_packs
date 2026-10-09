package io.github.fishstiz.packed_packs.transform.mixin.gui;

import io.github.fishstiz.packed_packs.Overrides;
import io.github.fishstiz.packed_packs.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.menu.resourcepacks.CustomResourcePackEntry;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePackEntry;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen;
import net.minecraft.client.resource.pack.ResourcePacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ResourcePacksScreen.class)
public abstract class ResourcePacksScreenMixin {
    @Shadow
    private List<ResourcePackEntry> availablePacks;
    @Shadow
    private List<ResourcePackEntry> selectedPacks;

    @Unique
    private static boolean packed_packs$isHidden(ResourcePackEntry entry) {
        return entry instanceof CustomResourcePackEntry custom && Overrides.isHidden(custom.getPack());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void packed_packs$hidePacks(CallbackInfo ci) {
        this.availablePacks.removeIf(ResourcePacksScreenMixin::packed_packs$isHidden);
        this.selectedPacks.removeIf(ResourcePacksScreenMixin::packed_packs$isHidden);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void packed_packs$arrangeFixedPacks(CallbackInfo ci) {
        List<ResourcePackEntry> arranged = new ArrayList<>(this.selectedPacks.size());
        for (Config.Position position : new Config.Position[]{Config.Position.TOP, null, Config.Position.BOTTOM}) {
            for (ResourcePackEntry entry : this.selectedPacks) {
                if (entry instanceof CustomResourcePackEntry custom && Overrides.position(custom.getPack()) == position) arranged.add(entry);
            }
        }
        for (ResourcePackEntry entry : this.selectedPacks) {
            if (!(entry instanceof CustomResourcePackEntry)) arranged.add(entry);
        }
        if (!arranged.equals(this.selectedPacks)) {
            this.selectedPacks.clear();
            this.selectedPacks.addAll(arranged);
        }
    }

    @ModifyArg(method = "buttonClicked", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resource/pack/ResourcePacks;setSelectedPacks(Ljava/util/List;)V"
    ))
    private List<ResourcePacks.UnopenedPack> packed_packs$keepHiddenPacks(List<ResourcePacks.UnopenedPack> packs) {
        List<ResourcePacks.UnopenedPack> previous = Minecraft.getInstance().getResourcePacks().getSelectedPacks();
        for (int i = 0; i < previous.size(); i++) {
            ResourcePacks.UnopenedPack pack = previous.get(i);
            if (!Overrides.isHidden(pack) || packs.contains(pack)) continue;
            int index = 0;
            for (int below = i - 1; below >= 0 && index == 0; below--) {
                index = packs.indexOf(previous.get(below)) + 1;
            }
            packs.add(index, pack);
        }
        return packs;
    }
}
