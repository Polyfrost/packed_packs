package io.github.fishstiz.packed_packs.transform.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourcePacksScreen.class)
public interface ResourcePacksScreenAccessor {
    @Accessor("parent")
    Screen packed_packs$getParent();
}
