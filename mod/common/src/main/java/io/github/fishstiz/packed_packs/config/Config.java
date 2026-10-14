package io.github.fishstiz.packed_packs.config;

import io.github.fishstiz.packed_packs.gui.components.SortOption;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.fishstiz.packed_packs.PackedPacks;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static @Nullable Config instance;
    public static final List<String> PREFERENCES = List.of("original_screen", "options", "action_bar", "toggle_incompatible", "folder_pack");
    public boolean replaceOriginal = true;
    public boolean applyOnClose = true;
    public boolean devMode;
    public boolean hideIncompatible;
    public boolean hideIncompatibleWarnings;
    public boolean foldersModuleByDefault;
    public boolean rememberLastViewedProfile;
    public SortOption sort = SortOption.NONE;
    public List<String> additionalFolders = new ArrayList<>();
    public boolean actionBar;
    public Map<String, Boolean> preferences = new HashMap<>();
    public @Nullable String activeProfile;
    public List<Profile> profiles = new ArrayList<>();

    private static Path getPath() {
        return PackedPacks.getConfigDir().resolve("config.json");
    }

    public static Config get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Config load() {
        try {
            Config config = GSON.fromJson(Files.readString(getPath()), Config.class);
            if (config != null) {
                if (config.sort == null) config.sort = SortOption.NONE;
                if (config.additionalFolders == null) config.additionalFolders = new ArrayList<>();
                if (config.profiles == null) config.profiles = new ArrayList<>();
                if (config.preferences == null) config.preferences = new HashMap<>();
                config.profiles.removeIf(profile -> profile == null || profile.name == null || profile.packs == null);
                for (Profile profile : config.profiles) {
                    if (profile.overrides == null) profile.overrides = new HashMap<>();
                    profile.overrides.values().removeIf(Objects::isNull);
                }
                return config;
            }
        } catch (NoSuchFileException ignored) {
        } catch (Exception e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to load file at '{}'.", getPath(), e);
        }
        return new Config();
    }

    public void save() {
        try {
            Files.createDirectories(getPath().getParent());
            Files.writeString(getPath(), GSON.toJson(this));
        } catch (Exception e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to save file at '{}'.", getPath(), e);
        }
    }

    private @Nullable Profile getProfile(@Nullable String name) {
        for (Profile profile : this.profiles) {
            if (profile.name.equals(name)) {
                return profile;
            }
        }
        return null;
    }

    public boolean pref(String widget) {
        return this.preferences.getOrDefault(widget, true);
    }

    public @Nullable Profile getActiveProfile() {
        return this.getProfile(this.activeProfile);
    }

    public @Nullable Profile getDefaultProfile() {
        return this.getProfile(DevConfig.get().defaultProfile);
    }

    public @Nullable PackOverride globalOverride(String id) {
        Profile profile = this.getDefaultProfile();
        return profile != null && profile != this.getActiveProfile() ? profile.overrides.get(id) : null;
    }

    private Stream<PackOverride> overrides(String id) {
        return Stream.of(this.getDefaultProfile(), this.getActiveProfile())
                .filter(Objects::nonNull)
                .distinct()
                .map(profile -> profile.overrides.get(id))
                .filter(Objects::nonNull);
    }

    public boolean isHidden(String id) {
        return this.overrides(id).anyMatch(override -> override.hidden);
    }

    public boolean isRequired(String id) {
        return this.overrides(id).anyMatch(override -> override.required);
    }

    public @Nullable Position position(String id) {
        return this.overrides(id).map(override -> override.position).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public enum Position {
        TOP, BOTTOM
    }

    public static final class PackOverride {
        public boolean hidden;
        public boolean required;
        public @Nullable Position position;

        public boolean isEmpty() {
            return !this.hidden && !this.required && this.position == null;
        }

        PackOverride copy() {
            PackOverride copy = new PackOverride();
            copy.hidden = this.hidden;
            copy.required = this.required;
            copy.position = this.position;
            return copy;
        }
    }

    public static final class Profile {
        public String name;
        public boolean locked;
        public List<String> packs = new ArrayList<>();
        public Map<String, PackOverride> overrides = new HashMap<>();

        public Profile(String name, List<String> packs) {
            this.name = name;
            this.packs = new ArrayList<>(packs);
        }

        public Profile copy() {
            Profile copy = new Profile(this.name, this.packs);
            this.overrides.forEach((id, override) -> copy.overrides.put(id, override.copy()));
            return copy;
        }
    }
}
