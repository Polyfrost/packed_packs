package io.github.fishstiz.packed_packs.config;

import io.github.fishstiz.packed_packs.gui.components.Entry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.fishstiz.packed_packs.PackedPacks;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

public final class DevConfig {
    public static final String REGEX_PREFIX = "regex:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static @Nullable DevConfig instance;
    private Packs resourcepacks = new Packs();

    private static Path getPath() {
        return PackedPacks.getConfigDir().resolve("config.meta.json");
    }

    public static Packs get() {
        if (instance == null) {
            instance = load();
        }
        return instance.resourcepacks;
    }

    private static DevConfig load() {
        try {
            DevConfig config = GSON.fromJson(Files.readString(getPath()), DevConfig.class);
            if (config != null) {
                if (config.resourcepacks == null) config.resourcepacks = new Packs();
                Packs packs = config.resourcepacks;
                if (packs.aliases == null) packs.aliases = new LinkedHashMap<>();
                if (packs.loadDefaultCondition == null) packs.loadDefaultCondition = LoadDefaultCondition.NO_OPTIONS_FILE;
                return config;
            }
        } catch (NoSuchFileException ignored) {
        } catch (Exception e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to load file at '{}'.", getPath(), e);
        }
        DevConfig config = new DevConfig();
        config.resourcepacks.loadDefaultCondition = LoadDefaultCondition.NO_OPTIONS_OR_VERSION_FILE;
        return config;
    }

    public static void save() {
        get();
        try {
            Files.createDirectories(getPath().getParent());
            Files.writeString(getPath(), GSON.toJson(instance));
        } catch (Exception e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to save file at '{}'.", getPath(), e);
        }
    }

    public enum LoadDefaultCondition {
        NO_OPTIONS_FILE, NO_OPTIONS_OR_VERSION_FILE
    }

    public static final class Packs {
        public Map<String, String> aliases = new LinkedHashMap<>();
        public @Nullable String defaultProfile;
        public LoadDefaultCondition loadDefaultCondition = LoadDefaultCondition.NO_OPTIONS_FILE;

        private static boolean matches(String pattern, String id) {
            if (!pattern.startsWith(REGEX_PREFIX)) return false;
            try {
                return id.matches(pattern.substring(REGEX_PREFIX.length()));
            } catch (PatternSyntaxException e) {
                PackedPacks.LOGGER.error("[packed_packs] Invalid regex syntax '{}'", pattern, e);
                return false;
            }
        }

        public @Nullable String resolve(String id, Collection<String> ids) {
            if (ids.contains(id)) return id;
            String target = this.aliases.get(id);
            if (target == null) {
                target = this.aliases.entrySet().stream()
                        .filter(alias -> matches(alias.getKey(), id))
                        .map(Map.Entry::getValue)
                        .findFirst().orElse(null);
            }
            if (target == null) return null;
            String canonical = target;
            return ids.stream().filter(existing -> existing.equals(canonical) || matches(canonical, existing)).findFirst().orElse(null);
        }

        public List<String> aliasesOf(String id) {
            return this.aliases.entrySet().stream().filter(alias -> alias.getValue().equals(id)).map(Map.Entry::getKey).toList();
        }

        public void setAliases(String id, List<String> aliases) {
            this.aliases.values().removeIf(id::equals);
            for (String alias : aliases) {
                this.aliases.put(alias, id);
            }
        }
    }
}
