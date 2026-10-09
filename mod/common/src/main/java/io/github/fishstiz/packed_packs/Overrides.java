package io.github.fishstiz.packed_packs;

import io.github.fishstiz.packed_packs.config.Config;
import net.minecraft.client.resource.pack.ResourcePacks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Overrides {
    private Overrides() {
    }

    private static @Nullable String key(String packId) {
        Config.Profile profile = Config.get().getDefaultProfile();
        if (profile == null || profile.overrides.isEmpty()) return null;
        if (profile.overrides.containsKey(packId)) return packId;
        for (int slash = packId.indexOf('/'); slash >= 0; slash = packId.indexOf('/', slash + 1)) {
            String folderId = packId.substring(0, slash + 1);
            if (profile.overrides.containsKey(folderId)) return folderId;
        }
        return null;
    }

    private static Config.@Nullable PackOverride of(ResourcePacks.UnopenedPack pack) {
        Config.Profile profile = Config.get().getDefaultProfile();
        String key = key(pack.getName());
        return profile != null && key != null ? profile.overrides.get(key) : null;
    }

    public static boolean isHidden(ResourcePacks.UnopenedPack pack) {
        Config.PackOverride override = of(pack);
        return override != null && override.hidden;
    }

    public static boolean isRequired(ResourcePacks.UnopenedPack pack) {
        Config.PackOverride override = of(pack);
        return override != null && override.required;
    }

    public static Config.@Nullable Position position(ResourcePacks.UnopenedPack pack) {
        Config.PackOverride override = of(pack);
        return override != null ? override.position : null;
    }

    public static List<ResourcePacks.UnopenedPack> enforce(List<ResourcePacks.UnopenedPack> selected, List<ResourcePacks.UnopenedPack> available) {
        List<ResourcePacks.UnopenedPack> packs = new ArrayList<>(selected);
        List<ResourcePacks.UnopenedPack> missing = available.stream().filter(pack -> isRequired(pack) && !selected.contains(pack)).toList();
        Set<String> folders = new HashSet<>();
        for (ResourcePacks.UnopenedPack pack : missing) {
            String key = key(pack.getName());
            if (key == null || !key.endsWith("/")) {
                packs.add(pack);
            } else if (folders.add(key)) {
                packs.addAll(Folders.contents(key, missing).reversed());
            }
        }

        List<ResourcePacks.UnopenedPack> enforced = new ArrayList<>(packs.size());
        for (Config.Position position : new Config.Position[]{Config.Position.BOTTOM, null, Config.Position.TOP}) {
            for (ResourcePacks.UnopenedPack pack : packs) {
                if (position(pack) == position) enforced.add(pack);
            }
        }
        if (enforced.equals(selected)) return selected;
        try {
            selected.clear();
            selected.addAll(enforced);
            return selected;
        } catch (UnsupportedOperationException e) {
            return enforced;
        }
    }
}
