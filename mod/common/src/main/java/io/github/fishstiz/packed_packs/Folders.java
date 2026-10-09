package io.github.fishstiz.packed_packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.fishstiz.packed_packs.config.Config;
import io.github.fishstiz.packed_packs.config.DevConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.pack.ResourcePacks;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public final class Folders {
    public static final String META_FILENAME = "packed_packs.folderpack.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RELATIVE_PREFIX = "relative/";
    private static final int MAX_DEPTH = 4;
    private static List<File> roots = List.of();

    private Folders() {
    }

    public record Meta(boolean module, List<String> packIds) {
    }

    private static boolean isPack(File file) {
        return file.isFile() && file.getName().endsWith(".zip") || file.isDirectory() && new File(file, "pack.mcmeta").isFile();
    }

    public static List<File> discover(File directory, List<File> vanilla) {
        List<File> folders = new ArrayList<>();
        folders.add(directory);
        for (String path : Config.get().additionalFolders) {
            File folder = new File(path);
            if (folder.isDirectory()) folders.add(folder);
        }
        roots = folders;

        List<File> packs = new ArrayList<>(vanilla);
        Set<String> ids = new HashSet<>();
        for (File pack : vanilla) ids.add(id(pack));
        for (File folder : folders) collect(folder, packs, ids, 0);
        return packs;
    }

    private static void collect(File folder, List<File> packs, Set<String> ids, int depth) {
        File[] children = folder.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (isPack(child)) {
                if (ids.add(id(child))) packs.add(child);
            } else if (child.isDirectory() && depth < MAX_DEPTH) {
                collect(child, packs, ids, depth + 1);
            }
        }
    }

    public static String id(File file) {
        Path path = file.toPath().toAbsolutePath();
        for (File root : roots) {
            Path rootPath = root.toPath().toAbsolutePath();
            if (path.startsWith(rootPath)) {
                return rootPath.relativize(path).toString().replace('\\', '/');
            }
        }
        return file.getName();
    }

    public static @Nullable File file(String id) {
        for (File root : roots) {
            File file = new File(root, id);
            if (file.exists()) return file;
        }
        return null;
    }

    public static int format(ResourcePacks.UnopenedPack pack) {
        try {
            return pack.getFormat();
        } catch (NullPointerException e) {
            return 0;
        }
    }

    public static Meta meta(File folder) {
        try {
            JsonObject json = GSON.fromJson(Files.readString(new File(folder, META_FILENAME).toPath()), JsonObject.class);
            if (json != null) {
                // folders were all modules before the property was added
                boolean module = !json.has("module") || !json.get("module").isJsonPrimitive() || json.get("module").getAsBoolean();
                List<String> packIds = new ArrayList<>();
                if (json.has("packIds") && json.get("packIds").isJsonArray()) {
                    json.getAsJsonArray("packIds").forEach(id -> packIds.add(id.getAsString()));
                }
                return new Meta(module, packIds);
            }
        } catch (NoSuchFileException ignored) {
        } catch (Exception e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to load folder pack metadata of '{}'.", folder, e);
        }
        return new Meta(Config.get().foldersModuleByDefault, List.of());
    }

    private static void save(File folder, Meta meta) {
        JsonObject json = new JsonObject();
        JsonArray packIds = new JsonArray();
        meta.packIds().forEach(packIds::add);
        json.addProperty("module", meta.module());
        json.add("packIds", packIds);
        try {
            Files.writeString(new File(folder, META_FILENAME).toPath(), GSON.toJson(json));
        } catch (IOException e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to save folder pack metadata of '{}'.", folder, e);
        }
    }

    public static void setModule(File folder, boolean module) {
        save(folder, new Meta(module, meta(folder).packIds()));
    }

    private static String stripSlash(String id) {
        return id.endsWith("/") ? id.substring(0, id.length() - 1) : id;
    }

    private static String normalize(String folderId, String savedId) {
        if (savedId.startsWith(RELATIVE_PREFIX)) return stripSlash(folderId + savedId.substring(RELATIVE_PREFIX.length()));
        return stripSlash(savedId.startsWith("file/") ? savedId.substring("file/".length()) : savedId);
    }

    public static List<String> order(String folderId, Collection<String> children) {
        File folder = file(folderId);
        List<String> saved = folder == null ? List.of() : meta(folder).packIds().stream().map(id -> normalize(folderId, id)).toList();
        return children.stream().sorted(Comparator.comparingInt(child -> {
            int index = saved.indexOf(stripSlash(child));
            return index < 0 ? Integer.MAX_VALUE : index;
        })).toList();
    }

    public static void setOrder(String folderId, List<String> visible, Collection<String> children) {
        File folder = file(folderId);
        if (folder == null) return;
        Meta meta = meta(folder);
        Set<String> visiblePaths = new HashSet<>();
        Set<String> childPaths = new HashSet<>();
        visible.forEach(id -> visiblePaths.add(stripSlash(id)));
        children.forEach(id -> childPaths.add(stripSlash(id)));

        List<String> packIds = new ArrayList<>();
        for (String id : visible) packIds.add(RELATIVE_PREFIX + stripSlash(id).substring(folderId.length()));
        int insertAt = 0;
        for (String id : meta.packIds()) {
            String path = normalize(folderId, id);
            if (!visiblePaths.contains(path) && childPaths.contains(path)) packIds.add(insertAt, id);
            insertAt = Math.min(insertAt + 1, packIds.size());
        }
        if (!packIds.equals(meta.packIds())) save(folder, new Meta(meta.module(), packIds));
    }

    public static List<ResourcePacks.UnopenedPack> contents(String folderId, Collection<ResourcePacks.UnopenedPack> packs) {
        Map<String, List<ResourcePacks.UnopenedPack>> byChild = new LinkedHashMap<>();
        for (ResourcePacks.UnopenedPack pack : packs) {
            String id = pack.getName();
            if (!id.startsWith(folderId)) continue;
            int slash = id.indexOf('/', folderId.length());
            byChild.computeIfAbsent(slash < 0 ? id : id.substring(0, slash + 1), child -> new ArrayList<>()).add(pack);
        }
        List<ResourcePacks.UnopenedPack> contents = new ArrayList<>();
        for (String child : order(folderId, byChild.keySet())) {
            contents.addAll(child.endsWith("/") ? contents(child, byChild.get(child)) : byChild.get(child));
        }
        return contents;
    }

    public static void onFirstReload(List<ResourcePacks.UnopenedPack> available, GameOptions options) {
        DevConfig.Packs dev = DevConfig.get();
        Map<String, ResourcePacks.UnopenedPack> byId = new LinkedHashMap<>();
        for (ResourcePacks.UnopenedPack pack : available) byId.put(pack.getName(), pack);

        Path version = PackedPacks.getConfigDir().resolve("__version.json");
        boolean noVersion = !Files.exists(version);
        if (noVersion) {
            try {
                Files.createDirectories(version.getParent());
                Files.writeString(version, "{\"version\": 1}");
            } catch (IOException e) {
                PackedPacks.LOGGER.error("[packed_packs] Failed to save file at '{}'.", version, e);
            }
        }

        Config.Profile profile = Config.get().getDefaultProfile();
        boolean noOptions = !Files.exists(FabricLoader.getInstance().getGameDir().resolve("options.txt"));
        if (profile != null && (noOptions || noVersion && dev.loadDefaultCondition == DevConfig.LoadDefaultCondition.NO_OPTIONS_OR_VERSION_FILE)) {
            PackedPacks.LOGGER.info("[packed_packs] Applying default resource pack profile due to load condition: {}", dev.loadDefaultCondition);
            options.resourcePacks.clear();
            options.incompatibleResourcePacks.clear();
            for (String id : profile.packs.reversed()) {
                String resolved = dev.resolve(id, byId.keySet());
                List<ResourcePacks.UnopenedPack> packs = id.endsWith("/") ? contents(id, available).reversed()
                        : resolved != null ? List.of(byId.get(resolved)) : List.of();
                for (ResourcePacks.UnopenedPack pack : packs) {
                    options.resourcePacks.add(pack.getName());
                    if (format(pack) != 1) options.incompatibleResourcePacks.add(pack.getName());
                }
            }
            return;
        }
        options.resourcePacks.replaceAll(id -> Objects.requireNonNullElse(dev.resolve(id, byId.keySet()), id));
    }
}
