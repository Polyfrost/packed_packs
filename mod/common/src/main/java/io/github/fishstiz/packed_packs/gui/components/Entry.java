package io.github.fishstiz.packed_packs.gui.components;

import io.github.fishstiz.packed_packs.Folders;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.pack.ResourcePacks;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public final class Entry {
    public static final Entry UP = new Entry("..", null, false, false);
    public final String id;
    public final ResourcePacks.@Nullable UnopenedPack pack;
    public final boolean module;
    public final boolean nested;
    public List<ResourcePacks.UnopenedPack> packs = List.of();
    private @Nullable File file;
    private long lastModified = -1;

    public Entry(String id, ResourcePacks.@Nullable UnopenedPack pack, boolean module, boolean nested) {
        this.id = id;
        this.pack = pack;
        this.module = module;
        this.nested = nested;
    }

    private static String path(String id) {
        return id.endsWith("/") ? id.substring(0, id.length() - 1) : id;
    }

    public static String parentOf(String id) {
        String path = path(id);
        return path.substring(0, path.lastIndexOf('/') + 1);
    }

    public String parent() {
        return parentOf(this.id);
    }

    public String name() {
        String path = path(this.id);
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public boolean isFolder() {
        return this.pack == null;
    }

    public boolean isLocked() {
        return this.module || this.nested;
    }

    public boolean transferable() {
        return !this.nested && (this.pack != null || this.module);
    }

    public boolean incompatible() {
        return this.pack != null ? Folders.format(this.pack) != 1 : this.packs.stream().anyMatch(pack -> Folders.format(pack) != 1);
    }

    public String incompatibility() {
        boolean old = this.pack != null ? Folders.format(this.pack) < 1 : this.packs.stream().anyMatch(pack -> Folders.format(pack) < 1);
        return old ? "old" : "new";
    }

    public String description() {
        if (this.pack != null) return this.pack.getDescription();
        if (this == UP) return "";
        return this.module ? I18n.translate("packed_packs.folder.locked") + " (" + this.packs.size() + ")" : I18n.translate("packed_packs.folder");
    }

    public @Nullable File file() {
        if (this.file == null && this != UP) this.file = Folders.file(this.id);
        return this.file;
    }

    public long lastModified() {
        if (this.lastModified < 0) this.lastModified = Folders.lastModified(this.id);
        return this.lastModified;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Entry entry && entry.id.equals(this.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }
}
