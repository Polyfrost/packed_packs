package io.github.fishstiz.packed_packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.github.fishstiz.packed_packs.config.Config;
import io.github.fishstiz.packed_packs.config.DevConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.resource.Identifier;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Folders {
    public static final String META_FILENAME = "packed_packs.folderpack.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RELATIVE_PREFIX = "relative/";
    private static final int MAX_DEPTH = 4;
    private static final int MAX_ICON_SIZE = 128;
    private static final Map<File, Meta> metas = new HashMap<>();
    private static List<File> roots = List.of();
    private static List<Path> rootPaths = List.of();
    private static List<File> scanned = List.of();
    private static List<File> failed = List.of();
    private static @Nullable CompletableFuture<Long> probe;
    private static Map<File, Stat> stats = Map.of();
    private static Map<String, Stat> statsById = Map.of();
    private static Map<String, String> cache;
    private static boolean fresh;
    private static boolean serving;
    private static long scannedStamp;
    private static Identifier defaultIcon;

    private Folders() {
    }

    public record Meta(boolean module, List<String> packIds) {
    }

    public record Stat(String id, boolean directory, long lastModified) {
        public String key() {
            return this.id + (this.directory ? ":folder:" : ":zip:") + this.lastModified;
        }
    }

    private static long metaModified(Path directory) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(directory.resolve("pack.mcmeta"), BasicFileAttributes.class);
            return attributes.isRegularFile() ? attributes.lastModifiedTime().toMillis() : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    public static List<File> discover(File directory) {
        List<File> folders = new ArrayList<>();
        folders.add(directory);
        for (String path : Config.get().additionalFolders) {
            File folder = new File(path);
            if (folder.isDirectory()) folders.add(folder);
        }
        roots = folders;
        rootPaths = folders.stream().map(folder -> folder.toPath().toAbsolutePath()).toList();

        metas.clear();

        Map<File, Stat> byFile = new LinkedHashMap<>();
        Map<String, Stat> byId = new HashMap<>();
        Collection<File> directories = new ConcurrentLinkedQueue<>();
        for (File folder : folders) {
            List<Object> ancestors = new ArrayList<>();
            try {
                Object key = Files.readAttributes(folder.toPath(), BasicFileAttributes.class).fileKey();
                if (key != null) ancestors.add(key);
            } catch (IOException ignored) {
            }
            for (Map.Entry<File, Stat> pack : collect(folder.toPath(), 0, ancestors, directories)) {
                if (byId.putIfAbsent(pack.getValue().id(), pack.getValue()) == null) byFile.put(pack.getKey(), pack.getValue());
            }
        }
        stats = byFile;
        scanned = new ArrayList<>(directories);
        statsById = byId;
        failed = List.of();
        probe = null;
        scannedStamp = stamp(scanned, failed);
        return new ArrayList<>(byFile.keySet());
    }

    private static List<Map.Entry<File, Stat>> collect(Path directory, int depth, List<Object> ancestors, Collection<File> directories) {
        directories.add(directory.toFile());
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            stream.forEach(children::add);
        } catch (IOException | DirectoryIteratorException e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to scan '{}'.", directory, e);
            return List.of();
        }
        children.sort(Comparator.comparing(Path::toFile));
        return children.parallelStream().flatMap(child -> {
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(child, BasicFileAttributes.class);
            } catch (IOException e) {
                return Stream.empty();
            }
            long meta = attributes.isDirectory() ? metaModified(child) : -1;
            if (meta >= 0 || attributes.isRegularFile() && child.getFileName().toString().endsWith(".zip")) {
                File pack = child.toFile();
                return Stream.of(Map.entry(pack, new Stat(id(pack), attributes.isDirectory(), Math.max(meta, attributes.lastModifiedTime().toMillis()))));
            } else if (attributes.isDirectory() && depth < MAX_DEPTH && !ancestors.contains(attributes.fileKey())) {
                List<Object> path = new ArrayList<>(ancestors);
                if (attributes.fileKey() != null) path.add(attributes.fileKey());
                return collect(child, depth + 1, path, directories).stream();
            }
            return Stream.empty();
        }).toList();
    }

    private static byte[] read(File pack, String name) throws IOException {
        if (stat(pack).directory()) return Files.readAllBytes(new File(pack, name).toPath());
        try (ZipFile zip = new ZipFile(pack)) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) throw new NoSuchFileException(name);
            try (InputStream stream = zip.getInputStream(entry)) {
                return stream.readAllBytes();
            }
        }
    }

    private static Path cachePath() {
        return PackedPacks.getConfigDir().resolve("__mcmeta_cache.json");
    }

    private static String cacheKey(File pack, Stat stat) {
        return pack.getAbsolutePath() + "|" + stat.lastModified();
    }

    private static Map<String, String> readCache() {
        try {
            Map<String, String> cached = GSON.fromJson(Files.readString(cachePath()), new TypeToken<Map<String, String>>() {
            }.getType());
            if (cached != null) return new HashMap<>(cached);
        } catch (NoSuchFileException ignored) {
        } catch (Exception e) {
            PackedPacks.LOGGER.warn("[packed_packs] Ignoring unreadable pack metadata cache.", e);
        }
        return new HashMap<>();
    }

    public static void preload(List<File> packs) {
        if (cache == null) cache = readCache();
        Map<String, String> read = new ConcurrentHashMap<>();
        packs.parallelStream().filter(pack -> !cache.containsKey(cacheKey(pack, stat(pack)))).forEach(pack -> {
            String mcmeta;
            try {
                mcmeta = new String(read(pack, "pack.mcmeta"), StandardCharsets.UTF_8);
            } catch (Exception e) {
                mcmeta = "";
            }
            read.put(cacheKey(pack, stat(pack)), mcmeta);
        });
        fresh = true;
        serving = true;
        if (read.isEmpty()) return;
        cache.putAll(read);
        Set<String> present = new HashSet<>();
        stats.forEach((pack, stat) -> present.add(cacheKey(pack, stat)));
        cache.keySet().retainAll(present);
        try {
            Files.createDirectories(cachePath().getParent());
            Files.writeString(cachePath(), new Gson().toJson(cache));
        } catch (IOException e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to save pack metadata cache.", e);
        }
    }

    public static void expire() {
        fresh = false;
    }

    public static void stopServing() {
        serving = false;
    }

    public static boolean stale() {
        return !fresh || scannedStamp != stamp(scanned, failed);
    }

    public static boolean expired() {
        return !fresh;
    }

    public static void loaded(Set<String> ids) {
        List<File> files = new ArrayList<>();
        stats.forEach((pack, stat) -> {
            if (!ids.contains(stat.id())) files.add(stat.directory() ? new File(pack, "pack.mcmeta") : pack);
        });
        failed = files;
        probe = null;
        scannedStamp = stamp(scanned, failed);
    }

    public static boolean changed() {
        if (probe == null) {
            List<File> directories = scanned;
            List<File> packs = failed;
            probe = CompletableFuture.supplyAsync(() -> stamp(directories, packs));
            return false;
        }
        if (!probe.isDone()) return false;
        long stamp = probe.join();
        probe = null;
        return stamp != scannedStamp;
    }

    public static byte @Nullable [] mcmeta(File pack) {
        Stat stat = fresh && serving ? stats.get(pack) : null;
        String mcmeta = stat != null ? cache.get(cacheKey(pack, stat)) : null;
        return mcmeta == null || mcmeta.isEmpty() ? null : mcmeta.getBytes(StandardCharsets.UTF_8);
    }

    public static @Nullable BufferedImage readIcon(File pack) {
        try {
            BufferedImage icon = ImageIO.read(new ByteArrayInputStream(read(pack, "pack.png")));
            if (icon == null || icon.getWidth() <= MAX_ICON_SIZE && icon.getHeight() <= MAX_ICON_SIZE) return icon;
            BufferedImage scaled = new BufferedImage(MAX_ICON_SIZE, MAX_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(icon, 0, 0, MAX_ICON_SIZE, MAX_ICON_SIZE, null);
            graphics.dispose();
            return scaled;
        } catch (Exception e) {
            return null;
        }
    }

    public static Identifier defaultIcon(TextureManager manager) {
        if (defaultIcon == null) {
            try {
                defaultIcon = manager.register("texturepackicon", new DynamicTexture(Minecraft.getInstance().getResourcePacks().defaultPack.getIcon()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return defaultIcon;
    }

    private static long stamp(List<File> directories, List<File> packs) {
        long stamp = 0;
        for (File directory : directories) stamp = stamp * 31 + directory.lastModified();
        for (File pack : packs) stamp = (stamp * 31 + pack.lastModified()) * 31 + pack.length();
        return stamp;
    }

    public static Stat stat(File file) {
        Stat stat = stats.get(file);
        return stat != null ? stat : new Stat(id(file), file.isDirectory(), file.lastModified());
    }

    public static long lastModified(String id) {
        Stat stat = statsById.get(id);
        if (stat != null) return stat.lastModified();
        File file = file(id);
        return file != null ? file.lastModified() : 0;
    }

    public static String id(File file) {
        Path path = file.toPath().toAbsolutePath();
        for (Path rootPath : rootPaths) {
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
        return metas.computeIfAbsent(folder, Folders::readMeta);
    }

    private static Meta readMeta(File folder) {
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
        metas.put(folder, meta);
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
        Map<String, Integer> saved = new HashMap<>();
        if (folder != null) {
            for (String id : meta(folder).packIds()) saved.putIfAbsent(normalize(folderId, id), saved.size());
        }
        return children.stream().sorted(Comparator.comparingInt(child -> saved.getOrDefault(stripSlash(child), Integer.MAX_VALUE))).toList();
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
