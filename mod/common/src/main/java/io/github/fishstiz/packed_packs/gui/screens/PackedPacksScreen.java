package io.github.fishstiz.packed_packs.gui.screens;

import io.github.fishstiz.packed_packs.gui.components.Entry;
import io.github.fishstiz.packed_packs.gui.components.PackList;
import io.github.fishstiz.packed_packs.Folders;
import io.github.fishstiz.packed_packs.PackedPacks;
import io.github.fishstiz.packed_packs.config.Config;
import io.github.fishstiz.packed_packs.config.DevConfig;
import io.github.fishstiz.packed_packs.transform.mixin.ResourcePacksScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.resource.Identifier;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public final class PackedPacksScreen extends Screen {
    private static final Identifier LOCK_ICON = icon("lock");
    private static final Identifier EXIT_ICON = icon("exit");
    private static final Identifier GEAR_ICON = icon("gear");
    private static final Identifier INCOMPATIBLE_ICON = icon("incompatible");
    private static final Identifier INCOMPATIBLE_HIDDEN_ICON = icon("incompatible_hidden");
    private static final Identifier EYE_SLASH_ICON = icon("eye_slash");
    private static final Identifier ARROW_UP_ICON = icon("arrow_up");
    private static final Identifier ARROW_DOWN_ICON = icon("arrow_down");
    private static final Identifier ALIAS_ICON = icon("alias");
    private static final String ALIAS_SEPARATOR = ";";
    private static final int DOUBLE_CLICK_MS = 300;
    private static final int HISTORY_LIMIT = 100;
    private static final int PROFILE_BUTTON = 0;
    private static final int SORT_BUTTON = 1;
    private static final int ORIGINAL_BUTTON = 2;
    private static final int FOLDER_BUTTON = 3;
    private static final int APPLY_BUTTON = 4;
    private static final int DONE_BUTTON = 5;
    private static final int OPTIONS_BUTTON = 6;
    private static final int INCOMPATIBLE_BUTTON = 7;
    private static @Nullable Screen original;

    private final Screen parent;
    private final Config config = Config.get();
    private final PackList available = new PackList(true);
    private final PackList selected = new PackList(false);
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Entry> owners = new HashMap<>();
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private PackList active = this.available;
    private boolean initialized;
    private boolean changed;
    private @Nullable Menu menu;
    private @Nullable String error;

    private @Nullable PackList dragSource;
    private boolean dragging;
    private int pressX, pressY;
    private int collapseTo = -1;
    private @Nullable Entry lastClicked;
    private long lastClickTime;

    public PackedPacksScreen(Screen parent) {
        this.parent = parent;
        if (!this.config.rememberLastViewedProfile) this.config.activeProfile = null;
        this.available.filter = entry -> this.isVisible(entry) && !(this.config.hideIncompatible && entry.incompatible());
        this.selected.filter = this::isVisible;
        this.available.badges = this::badges;
        this.selected.badges = this::badges;
        this.available.children = this::children;
        this.selected.children = this::children;
    }

    private static Identifier icon(String name) {
        return new Identifier(PackedPacks.MOD_ID, "textures/gui/sprites/icon/" + name + ".png");
    }

    public static Screen replace(Screen screen) {
        if (screen != null && screen.getClass() == ResourcePacksScreen.class && screen != original && Config.get().replaceOriginal) {
            return new PackedPacksScreen(((ResourcePacksScreenAccessor) screen).packed_packs$getParent());
        }
        return screen;
    }

    private ResourcePacks repository() {
        return this.minecraft.getResourcePacks();
    }

    private PackList other(PackList list) {
        return list == this.available ? this.selected : this.available;
    }

    private Config.@Nullable Profile profile() {
        return this.config.getActiveProfile();
    }

    private boolean isLocked() {
        Config.Profile profile = this.profile();
        return profile != null && profile.locked;
    }

    private boolean isVisible(Entry entry) {
        return this.config.devMode || !this.config.isHidden(entry.id);
    }

    private List<Identifier> badges(Entry entry) {
        List<Identifier> badges = new ArrayList<>();
        if (entry == Entry.UP) return badges;
        Config.Position position = this.config.position(entry.id);
        if (this.config.isHidden(entry.id)) badges.add(EYE_SLASH_ICON);
        if (this.config.isRequired(entry.id)) badges.add(LOCK_ICON);
        if (position != null) badges.add(position == Config.Position.TOP ? ARROW_UP_ICON : ARROW_DOWN_ICON);
        if (this.config.devMode && DevConfig.get().aliases.containsValue(entry.id)) badges.add(ALIAS_ICON);
        return badges;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);

        int columnWidth = Math.min(220, (this.width - 24) / 2);
        int left = this.width / 2 - 4 - columnWidth;
        int right = this.width / 2 + 4;
        int listHeight = this.height - 34 - 60;
        this.available.layout(left, 60, columnWidth, listHeight);
        this.selected.layout(right, 60, columnWidth, listHeight);
        this.reloadPacks();

        int bottomWidth = (columnWidth * 2 - 8) / 3;
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(PROFILE_BUTTON, left, 18, columnWidth, 20, ""));
        this.buttons.add(new ButtonWidget(SORT_BUTTON, right, 18, columnWidth - 72, 20, ""));
        this.buttons.add(new ButtonWidget(ORIGINAL_BUTTON, right + columnWidth - 20, 18, 20, 20, ""));
        this.buttons.add(new ButtonWidget(FOLDER_BUTTON, left, this.height - 28, bottomWidth, 20, I18n.translate("resourcePack.openFolder")));
        this.buttons.add(new ButtonWidget(APPLY_BUTTON, left + bottomWidth + 8, this.height - 28, bottomWidth, 20, I18n.translate("packed_packs.apply")));
        this.buttons.add(new ButtonWidget(DONE_BUTTON, left + (bottomWidth + 8) * 2, this.height - 28, bottomWidth, 20, I18n.translate("gui.done")));
        this.buttons.add(new ButtonWidget(OPTIONS_BUTTON, right + columnWidth - 44, 18, 20, 20, ""));
        this.buttons.add(new ButtonWidget(INCOMPATIBLE_BUTTON, right + columnWidth - 68, 18, 20, 20, ""));
        this.updateButtons();
    }

    private void updateButtons() {
        Config.Profile profile = this.profile();
        this.buttons.get(PROFILE_BUTTON).message = profile != null ? profile.name : I18n.translate("packed_packs.profile.none");
        this.buttons.get(SORT_BUTTON).message = I18n.translate("packed_packs.sort") + ": " + I18n.translate(this.config.sort.translationKey());
        this.buttons.get(APPLY_BUTTON).active = this.changed;
    }

    private void reloadPacks() {
        ResourcePacks repository = this.repository();
        repository.reload();
        List<ResourcePacks.UnopenedPack> all = repository.getAvailablePacks();

        this.entries.clear();
        this.owners.clear();
        for (ResourcePacks.UnopenedPack pack : all) {
            String id = pack.getName();
            Entry owner = null;
            for (int slash = id.indexOf('/'); slash >= 0; slash = id.indexOf('/', slash + 1)) {
                boolean nested = owner != null;
                Entry folder = this.entries.computeIfAbsent(id.substring(0, slash + 1), folderId -> {
                    File file = Folders.file(folderId);
                    return new Entry(folderId, null, !nested && file != null && Folders.meta(file).module(), nested);
                });
                if (folder.module) owner = folder;
            }
            Entry entry = new Entry(id, pack, false, owner != null);
            this.entries.put(id, entry);
            this.owners.put(id, owner != null ? owner : entry);
        }
        for (PackList list : List.of(this.available, this.selected)) {
            Entry folder = this.entries.get(list.folder);
            if (folder == null || list == this.selected && !folder.isLocked()) list.folder = "";
        }
        for (Entry entry : this.entries.values()) {
            if (entry.module) entry.packs = Folders.contents(entry.id, all);
        }

        if (!this.initialized) {
            this.initialized = true;
            List<ResourcePacks.UnopenedPack> enabled = new ArrayList<>(repository.getSelectedPacks());
            Collections.reverse(enabled);
            for (ResourcePacks.UnopenedPack pack : enabled) {
                Entry entry = this.owners.get(pack.getName());
                if (entry != null && !this.selected.packs.contains(entry)) this.selected.packs.add(entry);
            }
        }
        this.sync();
        if (this.enforce()) this.changed = true;
        this.available.refresh();
        this.selected.refresh();
    }

    private void sync() {
        this.selected.packs.replaceAll(entry -> this.entries.getOrDefault(entry.id, entry));
        if (this.selected.packs.removeIf(entry -> !entry.transferable() || !this.entries.containsKey(entry.id))) {
            this.changed = true;
        }
        this.available.packs.replaceAll(entry -> this.entries.getOrDefault(entry.id, entry));
        this.available.packs.removeIf(entry -> entry.nested || !this.entries.containsKey(entry.id) || this.selected.packs.contains(entry));
        for (Entry entry : this.entries.values()) {
            if (!entry.nested && !this.selected.packs.contains(entry) && !this.available.packs.contains(entry)) {
                this.available.packs.add(entry);
            }
        }
    }

    private boolean enforce() {
        List<Entry> packs = this.selected.packs;
        List<Entry> before = List.copyOf(packs);
        for (Entry entry : List.copyOf(this.available.packs)) {
            if (entry.transferable() && this.config.isRequired(entry.id)) {
                this.available.packs.remove(entry);
                packs.add(entry);
            }
        }
        List<Entry> top = packs.stream().filter(entry -> this.config.position(entry.id) == Config.Position.TOP).toList();
        List<Entry> bottom = packs.stream().filter(entry -> this.config.position(entry.id) == Config.Position.BOTTOM).toList();
        packs.removeAll(top);
        packs.removeAll(bottom);
        packs.addAll(0, top);
        packs.addAll(bottom);
        return !before.equals(packs);
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        this.config.save();
    }

    private void close(boolean apply) {
        if (apply) this.apply();
        this.minecraft.openScreen(this.parent);
    }

    private List<ResourcePacks.UnopenedPack> enabledPacks() {
        List<ResourcePacks.UnopenedPack> enabled = new ArrayList<>();
        for (Entry entry : this.selected.packs) {
            if (entry.pack != null) {
                enabled.add(entry.pack);
            } else {
                enabled.addAll(entry.packs);
            }
        }
        Collections.reverse(enabled);
        return enabled;
    }

    private void apply() {
        if (!this.changed) return;
        this.changed = false;

        ResourcePacks repository = this.repository();
        List<ResourcePacks.UnopenedPack> enabled = this.enabledPacks();
        for (ResourcePacks.UnopenedPack pack : repository.getSelectedPacks()) {
            if (!enabled.contains(pack)) pack.close();
        }
        repository.setSelectedPacks(enabled);
        this.minecraft.options.resourcePacks.clear();
        this.minecraft.options.incompatibleResourcePacks.clear();
        for (ResourcePacks.UnopenedPack pack : enabled) {
            this.minecraft.options.resourcePacks.add(pack.getName());
            if (Folders.format(pack) != 1) {
                this.minecraft.options.incompatibleResourcePacks.add(pack.getName());
            }
        }
        this.minecraft.options.save();
        this.minecraft.reloadResources();
        this.updateButtons();
    }

    private record Snapshot(List<Entry> available, List<Entry> selected) {
    }

    private Snapshot snapshot() {
        return new Snapshot(List.copyOf(this.available.packs), List.copyOf(this.selected.packs));
    }

    private boolean beginChange() {
        if (this.isLocked()) return false;
        this.undo.push(this.snapshot());
        if (this.undo.size() > HISTORY_LIMIT) this.undo.removeLast();
        this.redo.clear();
        return true;
    }

    private void endChange() {
        this.enforce();
        this.available.refresh();
        this.selected.refresh();
        Config.Profile profile = this.profile();
        if (profile != null && !profile.locked) {
            profile.packs = new ArrayList<>(this.selected.packs.stream().map(entry -> entry.id).toList());
        }
        this.changed = true;
        this.updateButtons();
    }

    private void overridesChanged() {
        if (this.enforce()) {
            this.endChange();
        } else {
            this.available.refresh();
            this.selected.refresh();
        }
    }

    private void clearHistory() {
        this.undo.clear();
        this.redo.clear();
    }

    private void restore(Deque<Snapshot> from, Deque<Snapshot> to) {
        if (from.isEmpty() || this.isLocked()) return;
        to.push(this.snapshot());
        this.setPacks(from.pop());
    }

    private void setPacks(Snapshot snapshot) {
        this.available.packs.clear();
        this.available.packs.addAll(snapshot.available());
        this.selected.packs.clear();
        this.selected.packs.addAll(snapshot.selected());
        this.sync();
        this.endChange();
    }

    private void transfer(PackList from, PackList to, int index, boolean follow) {
        List<Entry> moved = from.selected().stream()
                .filter(entry -> entry.transferable() && (to != this.available || from == to || !this.config.isRequired(entry.id)))
                .toList();
        if (moved.isEmpty() || (from == to && to == this.available) || !this.beginChange()) return;

        int insert = index >= 0 ? index : to == this.selected ? 0 : to.packs.size();
        if (from == to) {
            insert -= (int) to.packs.subList(0, insert).stream().filter(moved::contains).count();
        }
        from.packs.removeAll(moved);
        to.packs.addAll(Math.min(insert, to.packs.size()), moved);
        this.endChange();

        if (follow) {
            this.active = to;
            from.selection.clear();
            to.selection.addAll(moved);
        } else {
            from.moveFocus(0, false);
        }
    }

    private void shift(int direction) {
        List<Entry> moved = this.selected.selected();
        if (moved.isEmpty() || !this.beginChange()) return;
        shift(this.selected.packs, moved, direction);
        this.endChange();
    }

    private static void shift(List<Entry> packs, List<Entry> moved, int direction) {
        for (Entry entry : direction > 0 ? moved.reversed() : moved) {
            int index = packs.indexOf(entry);
            int target = index + direction;
            if (target >= 0 && target < packs.size() && !moved.contains(packs.get(target))) {
                Collections.swap(packs, index, target);
            }
        }
    }

    private List<Entry> children(String folder) {
        List<String> ids = this.entries.values().stream().filter(entry -> entry.parent().equals(folder)).map(entry -> entry.id).toList();
        return Folders.order(folder, ids).stream().map(this.entries::get).toList();
    }

    private void openFolder(PackList list, String folder) {
        list.folder = folder;
        list.selection.clear();
        list.refresh();
        this.active = list;
    }

    private boolean navigate(PackList list, @Nullable Entry entry) {
        if (entry == Entry.UP) {
            String parent = Entry.parentOf(list.folder);
            Entry folder = this.entries.get(parent);
            this.openFolder(list, list == this.selected && (folder == null || !folder.isLocked()) ? "" : parent);
        } else if (entry != null && entry.isFolder() && !entry.transferable()) {
            this.openFolder(list, entry.id);
        } else {
            return false;
        }
        return true;
    }

    private void reorder(PackList list, List<Entry> rows) {
        List<ResourcePacks.UnopenedPack> enabled = this.enabledPacks();
        Entry focused = list.focused();
        Folders.setOrder(list.folder, rows.stream().map(entry -> entry.id).toList(), this.children(list.folder).stream().map(entry -> entry.id).toList());
        this.reloadPacks();
        list.setFocus(list.view().indexOf(focused));
        if (!enabled.equals(this.enabledPacks())) this.changed = true;
        this.updateButtons();
    }

    private void shiftInFolder(PackList list, int direction) {
        List<Entry> rows = new ArrayList<>(list.rows());
        List<Entry> moved = list.selected();
        if (moved.isEmpty()) return;
        shift(rows, moved, direction);
        this.reorder(list, rows);
    }

    private void dropInFolder(PackList list, int mouseY) {
        List<Entry> rows = new ArrayList<>(list.rows());
        List<Entry> moved = list.selected();
        List<Entry> view = list.view();
        Entry next = view.subList(list.dropRow(mouseY), view.size()).stream()
                .filter(entry -> entry != Entry.UP && !moved.contains(entry))
                .findFirst().orElse(null);
        rows.removeAll(moved);
        rows.addAll(next != null ? rows.indexOf(next) : rows.size(), moved);
        this.reorder(list, rows);
    }

    private void recall(String folder) {
        List<Entry> moved = this.selected.packs.stream().filter(entry -> entry.id.startsWith(folder) && !this.config.isRequired(entry.id)).toList();
        if (moved.isEmpty() || !this.beginChange()) return;
        this.selected.packs.removeAll(moved);
        this.available.packs.addAll(moved);
        this.endChange();
    }

    private void setModule(Entry folder, boolean module) {
        File file = folder.file();
        if (file == null) return;
        Folders.setModule(file, module);
        this.clearHistory();
        this.reloadPacks();
    }

    private @Nullable Entry find(String id) {
        Entry entry = this.entries.get(id);
        if (entry == null || entry.nested) {
            String resolved = DevConfig.get().resolve(id, this.owners.keySet());
            entry = resolved != null ? this.owners.get(resolved) : null;
        }
        return entry != null && entry.transferable() ? entry : null;
    }

    private void setProfile(Config.@Nullable Profile profile) {
        this.config.activeProfile = profile != null ? profile.name : null;
        this.clearHistory();
        if (profile != null) {
            List<Entry> enabled = new ArrayList<>();
            for (String id : profile.packs) {
                Entry entry = this.find(id);
                if (entry != null && !enabled.contains(entry)) enabled.add(entry);
            }
            List<Entry> rest = new ArrayList<>(this.available.packs);
            rest.addAll(this.selected.packs);
            rest.removeAll(enabled);
            boolean locked = profile.locked;
            profile.locked = true;
            this.setPacks(new Snapshot(rest, enabled));
            profile.locked = locked;
        } else {
            this.overridesChanged();
        }
        this.updateButtons();
    }

    private String uniqueProfileName(String name) {
        String unique = name;
        for (int i = 2; this.hasProfile(unique); i++) {
            unique = name + " (" + i + ")";
        }
        return unique;
    }

    private boolean hasProfile(String name) {
        return this.config.profiles.stream().anyMatch(profile -> profile.name.equals(name));
    }

    private void addProfile(Config.Profile profile) {
        profile.name = this.uniqueProfileName(profile.name);
        this.config.profiles.add(profile);
        this.setProfile(profile);
    }

    private void openProfileMenu(ButtonWidget button) {
        Menu menu = new Menu(button.x, button.y + 20, button.getWidth());
        DevConfig.Packs dev = DevConfig.get();
        Config.Profile active = this.profile();
        menu.add((active == null ? "> " : "") + I18n.translate("packed_packs.profile.none"), () -> this.setProfile(null));
        for (Config.Profile profile : this.config.profiles) {
            String star = profile.name.equals(dev.defaultProfile) ? " ★" : "";
            menu.add((profile == active ? "> " : "") + profile.name + star, () -> this.setProfile(profile));
        }
        menu.add("+ " + I18n.translate("packed_packs.profile.new"), () -> this.minecraft.openScreen(new TextPromptScreen(
                this, I18n.translate("packed_packs.profile.new.info"), I18n.translate("packed_packs.profile.unnamed"),
                name -> this.addProfile(new Config.Profile(name, this.selected.packs.stream().map(entry -> entry.id).toList()))
        )));
        if (active != null) {
            boolean isDefault = active.name.equals(dev.defaultProfile);
            menu.add(I18n.translate("packed_packs.profile.edit"), () -> this.minecraft.openScreen(new TextPromptScreen(
                    this, I18n.translate("packed_packs.profile.edit"), active.name, name -> {
                if (!name.equals(active.name)) {
                    active.name = this.uniqueProfileName(name);
                    this.config.activeProfile = active.name;
                    if (isDefault) {
                        dev.defaultProfile = active.name;
                        DevConfig.save();
                    }
                }
            })));
            menu.add(I18n.translate("packed_packs.profile.copy"), () -> this.addProfile(active.copy()));
            menu.add(I18n.translate(active.locked ? "packed_packs.profile.unlock" : "packed_packs.profile.lock"), () -> active.locked = !active.locked);
            menu.add(I18n.translate("packed_packs.profile.delete.info"), () -> {
                this.config.profiles.remove(active);
                if (isDefault) {
                    dev.defaultProfile = null;
                    DevConfig.save();
                }
                this.setProfile(null);
            });
            if (this.config.devMode) {
                menu.add(I18n.translate(isDefault ? "packed_packs.profile.default.unset" : "packed_packs.profile.default.set"), () -> {
                    dev.defaultProfile = isDefault ? null : active.name;
                    DevConfig.save();
                    this.overridesChanged();
                });
            }
        }
        if (this.config.devMode) {
            boolean version = dev.loadDefaultCondition == DevConfig.LoadDefaultCondition.NO_OPTIONS_OR_VERSION_FILE;
            String key = "packed_packs.profile.default.resource_pack.load";
            menu.add(I18n.translate(key) + ": " + I18n.translate(key + (version ? ".no_options_or_version" : ".no_options")), () -> {
                dev.loadDefaultCondition = version ? DevConfig.LoadDefaultCondition.NO_OPTIONS_FILE : DevConfig.LoadDefaultCondition.NO_OPTIONS_OR_VERSION_FILE;
                DevConfig.save();
            });
        }
        this.menu = menu;
    }

    private List<ResourcePacks.UnopenedPack> packsIn(Entry entry) {
        return entry.pack != null ? List.of(entry.pack)
                : this.repository().getAvailablePacks().stream().filter(pack -> pack.getName().startsWith(entry.id)).toList();
    }

    private void openPackMenu(PackList list, int mouseX, int mouseY) {
        List<Entry> packs = list.selected();
        Menu menu = new Menu(mouseX, mouseY, 120);
        if (packs.stream().anyMatch(Entry::transferable)) {
            menu.add(I18n.translate("packed_packs.transfer"), () -> this.transfer(list, this.other(list), -1, false));
        }
        File file = packs.size() == 1 ? packs.getFirst().file() : null;
        if (file != null) {
            Entry entry = packs.getFirst();
            if (entry.isFolder()) {
                menu.add(I18n.translate("packed_packs.folder.open"), () -> this.openFolder(list, entry.id));
                if (!entry.nested) {
                    menu.add(I18n.translate(entry.module ? "packed_packs.folder.unlock" : "packed_packs.folder.lock"), () -> this.setModule(entry, !entry.module));
                }
            }
            menu.add(I18n.translate("packed_packs.file.open"), () -> PackedPacks.open(file));
            menu.add(I18n.translate("packed_packs.file.rename"), () -> this.minecraft.openScreen(new TextPromptScreen(
                    this, I18n.translate("packed_packs.file.rename"), entry.name(), name -> this.rename(entry, name)
            )));
            if (this.config.devMode) this.addDevItems(menu, entry);
        }
        if (!packs.isEmpty()) {
            menu.add(I18n.translate("packed_packs.file.delete"), () -> this.minecraft.openScreen(new ConfirmScreen((confirmed, id) -> {
                if (confirmed) this.delete(packs);
                this.minecraft.openScreen(this);
            }, I18n.translate("packed_packs.file.delete"), String.join(", ", packs.stream().map(entry -> entry.id).toList()), 0)));
        }
        String opened = list.folder;
        if (list == this.available && !opened.isEmpty() && this.selected.packs.stream().anyMatch(entry -> entry.id.startsWith(opened))) {
            menu.add(I18n.translate("packed_packs.folder.recall"), () -> this.recall(opened));
        }
        File folder = file != null ? file.getParentFile() : this.repository().getDirectory();
        menu.add(I18n.translate("packed_packs.file.parent.open"), () -> PackedPacks.open(folder));
        menu.add(I18n.translate("packed_packs.refresh"), this::reloadPacks);
        this.menu = menu;
    }

    private void addDevItems(Menu menu, Entry entry) {
        String id = entry.id;
        Config.Profile profile = this.profile();
        if (profile != null && entry.transferable()) {
            Config.PackOverride global = this.config.globalOverride(id);
            Config.Position position = this.config.position(id);
            String on = I18n.translate("options.on");
            String off = I18n.translate("options.off");
            String key = "packed_packs.profile.override.";
            menu.add(I18n.translate(key + "hidden") + ": " + (this.config.isHidden(id) ? on : off),
                    global != null && global.hidden ? null : () -> this.override(profile, id, override -> override.hidden = !override.hidden));
            menu.add(I18n.translate(key + "required") + ": " + (this.config.isRequired(id) ? on : off),
                    global != null && global.required ? null : () -> this.override(profile, id, override -> override.required = !override.required));
            menu.add(I18n.translate(key + "fixed") + ": " + (position == null ? off : I18n.translate(key + (position == Config.Position.TOP ? "fixed.top" : "fixed.bottom"))),
                    global != null && global.position != null ? null : () -> this.override(profile, id, override -> override.position =
                            override.position == null ? Config.Position.TOP : override.position == Config.Position.TOP ? Config.Position.BOTTOM : null));
            if (profile.overrides.containsKey(id)) {
                menu.add(I18n.translate(key + "remove"), () -> {
                    profile.overrides.remove(id);
                    this.overridesChanged();
                });
            }
        }
        menu.add(I18n.translate("chat.copy"), () -> setClipboard(id));
        menu.add(I18n.translate("packed_packs.aliases.edit"), () -> this.minecraft.openScreen(new TextPromptScreen(
                this, I18n.translate("packed_packs.aliases.edit") + " (" + ALIAS_SEPARATOR + "): " + id,
                String.join(ALIAS_SEPARATOR, DevConfig.get().aliasesOf(id)), true,
                aliases -> {
                    DevConfig.get().setAliases(id, Arrays.stream(aliases.split(ALIAS_SEPARATOR)).map(String::trim).filter(alias -> !alias.isEmpty()).toList());
                    DevConfig.save();
                }
        )));
    }

    private void override(Config.Profile profile, String id, Consumer<Config.PackOverride> change) {
        Config.PackOverride override = profile.overrides.computeIfAbsent(id, key -> new Config.PackOverride());
        change.accept(override);
        if (override.isEmpty()) profile.overrides.remove(id);
        this.overridesChanged();
    }

    private void rename(Entry entry, String name) {
        File source = entry.file();
        if (source == null) return;
        File target = new File(source.getParentFile(), name);
        boolean valid = name.equals(target.getName()) && !name.equals(".") && !name.equals("..") && !target.exists();
        this.packsIn(entry).forEach(ResourcePacks.UnopenedPack::close);
        if (!valid || !source.renameTo(target)) {
            this.error = I18n.translate("packed_packs.file.rename.fail", entry.name(), name);
            return;
        }
        int index = this.selected.packs.indexOf(entry);
        this.reloadPacks();
        this.clearHistory();
        Entry renamed = this.entries.get(entry.parent() + name + (entry.isFolder() ? "/" : ""));
        if (index >= 0 && renamed != null && renamed.transferable() && this.available.packs.remove(renamed)) {
            this.selected.packs.add(Math.min(index, this.selected.packs.size()), renamed);
            this.endChange();
        }
    }

    private void delete(List<Entry> entries) {
        this.clearHistory();
        for (Entry entry : entries) {
            File file = entry.file();
            this.packsIn(entry).forEach(ResourcePacks.UnopenedPack::close);
            if (file == null || !FileUtils.deleteQuietly(file)) {
                this.error = I18n.translate("packed_packs.file.delete.fail", entry.id);
            } else if (this.selected.packs.contains(entry)) {
                this.changed = true;
            }
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        switch (button.id) {
            case PROFILE_BUTTON -> this.openProfileMenu(button);
            case SORT_BUTTON -> {
                this.config.sort = this.config.sort.next();
                this.available.refresh();
                this.updateButtons();
            }
            case ORIGINAL_BUTTON -> {
                original = new ResourcePacksScreen(this.parent);
                this.minecraft.openScreen(original);
            }
            case FOLDER_BUTTON -> PackedPacks.open(this.repository().getDirectory());
            case APPLY_BUTTON -> this.apply();
            case DONE_BUTTON -> this.close(true);
            case OPTIONS_BUTTON -> this.minecraft.openScreen(new OptionsScreen(this));
            case INCOMPATIBLE_BUTTON -> {
                this.config.hideIncompatible = !this.config.hideIncompatible;
                this.available.refresh();
            }
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && this.menu == null) {
            int mouseX = Mouse.getEventX() * this.width / this.minecraft.width;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.minecraft.height - 1;
            for (PackList list : List.of(this.available, this.selected)) {
                if (list.contains(mouseX, mouseY)) {
                    list.scroll(-Integer.signum(wheel) * Math.max(1, Math.abs(wheel) * PackList.ROW_HEIGHT / 240));
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.error = null;
        if (this.menu != null) {
            Menu menu = this.menu;
            this.menu = null;
            menu.click(mouseX, mouseY);
            this.updateButtons();
            return;
        }
        if (button == 3) {
            this.restore(this.undo, this.redo);
            return;
        }
        if (button == 4) {
            this.restore(this.redo, this.undo);
            return;
        }

        super.mouseClicked(mouseX, mouseY, button);
        this.available.search.mouseClicked(mouseX, mouseY, button);
        this.selected.search.mouseClicked(mouseX, mouseY, button);

        PackList list = this.available.contains(mouseX, mouseY) ? this.available : this.selected.contains(mouseX, mouseY) ? this.selected : null;
        if (list == null || this.menu != null || this.minecraft.screen != this) return;

        this.active = list;
        int index = list.indexAt(mouseY);
        Entry entry = index >= 0 ? list.view().get(index) : null;
        boolean toggle = isControlDown();
        boolean range = isShiftDown();

        if (button == 1) {
            if (entry == null || !list.selection.contains(entry)) list.click(index, false, false);
            this.openPackMenu(list, mouseX, mouseY);
        } else if (button == 0) {
            long now = Minecraft.getTime();
            if (entry != null && entry.equals(this.lastClicked) && now - this.lastClickTime < DOUBLE_CLICK_MS && !toggle && !range) {
                this.lastClicked = null;
                if (!this.navigate(list, entry)) this.transfer(list, this.other(list), -1, false);
                return;
            }
            this.lastClicked = entry;
            this.lastClickTime = now;

            if (entry != null && list.selection.contains(entry) && !toggle && !range) {
                this.collapseTo = index;
                list.setFocus(index);
            } else {
                list.click(index, toggle, range);
            }
            if (entry != null) {
                this.dragSource = list;
                this.pressX = mouseX;
                this.pressY = mouseY;
            }
        }
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long duration) {
        if (this.dragSource != null && !this.isLocked() && (Math.abs(mouseX - this.pressX) > 3 || Math.abs(mouseY - this.pressY) > 3)) {
            this.dragging = true;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        if (button != 0) return;

        PackList source = this.dragSource;
        if (source != null && this.dragging) {
            PackList target = this.dropTarget(mouseX, mouseY);
            if (target == source && !target.isRoot()) {
                this.dropInFolder(target, mouseY);
            } else if (target != null) {
                this.transfer(source, target, target.isRoot() ? target.dropIndex(mouseY) : -1, true);
            }
        } else if (source != null && this.collapseTo >= 0) {
            source.click(this.collapseTo, false, false);
        }
        this.dragSource = null;
        this.dragging = false;
        this.collapseTo = -1;
    }

    private @Nullable PackList dropTarget(int mouseX, int mouseY) {
        PackList list = this.selected.contains(mouseX, mouseY) ? this.selected : this.available.contains(mouseX, mouseY) ? this.available : null;
        if (list != this.dragSource) return list;
        return list == this.selected && list.isRoot() || list.reorderable() ? list : null;
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (this.menu != null) {
            if (key == Keyboard.KEY_ESCAPE) this.menu = null;
            return;
        }

        for (PackList list : List.of(this.available, this.selected)) {
            if (list.search.isFocused()) {
                if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_TAB) {
                    list.search.setFocused(false);
                } else if (list.search.keyPressed(chr, key)) {
                    list.refresh();
                }
                return;
            }
        }

        boolean control = isControlDown();
        boolean shift = isShiftDown();
        if (key == Keyboard.KEY_ESCAPE) {
            this.close(this.config.applyOnClose);
        } else if (control && key == Keyboard.KEY_Z) {
            this.restore(shift ? this.redo : this.undo, shift ? this.undo : this.redo);
        } else if (control && key == Keyboard.KEY_Y) {
            this.restore(this.redo, this.undo);
        } else if (control && key == Keyboard.KEY_A) {
            this.active.selectAll();
        } else if (control && key == Keyboard.KEY_F) {
            this.active.search.setFocused(true);
        } else if (key == Keyboard.KEY_F5) {
            this.reloadPacks();
            this.updateButtons();
        } else if (key == Keyboard.KEY_TAB) {
            this.focus(this.other(this.active));
        } else if (key == Keyboard.KEY_LEFT) {
            this.focus(this.available);
        } else if (key == Keyboard.KEY_RIGHT) {
            this.focus(this.selected);
        } else if (key == Keyboard.KEY_BACK) {
            if (!this.active.isRoot()) this.navigate(this.active, Entry.UP);
        } else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            int direction = key == Keyboard.KEY_UP ? -1 : 1;
            if (!control && !isAltDown()) {
                this.active.moveFocus(direction, shift);
            } else if (this.active.reorderable()) {
                this.shiftInFolder(this.active, direction);
            } else if (this.active == this.selected) {
                this.shift(direction);
            }
        } else if (key == Keyboard.KEY_SPACE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (!this.navigate(this.active, this.active.focused())) {
                this.transfer(this.active, this.other(this.active), -1, false);
            }
        }
    }

    private void focus(PackList list) {
        this.active = list;
        if (list.selection.isEmpty()) list.moveFocus(0, false);
    }

    @Override
    public void tick() {
        this.available.search.tick();
        this.selected.search.tick();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.drawBackgroundTexture(0);
        this.drawCenteredString(this.textRenderer, I18n.translate("resourcePack.title"), this.width / 2, 6, 0xFFFFFF);
        int widgetMouseX = this.menu != null ? -1 : mouseX;
        int widgetMouseY = this.menu != null ? -1 : mouseY;

        this.available.render(widgetMouseX, widgetMouseY, this.active == this.available, I18n.translate("resourcePack.available.title"));
        this.selected.render(widgetMouseX, widgetMouseY, this.active == this.selected, I18n.translate("resourcePack.selected.title"));
        super.render(widgetMouseX, widgetMouseY, tickDelta);

        ButtonWidget originalButton = this.buttons.get(ORIGINAL_BUTTON);
        ButtonWidget optionsButton = this.buttons.get(OPTIONS_BUTTON);
        ButtonWidget incompatibleButton = this.buttons.get(INCOMPATIBLE_BUTTON);
        this.drawIcon(EXIT_ICON, originalButton.x + 2, originalButton.y + 2);
        this.drawIcon(GEAR_ICON, optionsButton.x + 2, optionsButton.y + 2);
        this.drawIcon(this.config.hideIncompatible ? INCOMPATIBLE_HIDDEN_ICON : INCOMPATIBLE_ICON, incompatibleButton.x + 2, incompatibleButton.y + 2);
        if (this.isLocked()) {
            ButtonWidget profileButton = this.buttons.get(PROFILE_BUTTON);
            this.drawIcon(LOCK_ICON, profileButton.x + 2, profileButton.y + 2);
        }
        if (this.error != null) {
            this.drawCenteredString(this.textRenderer, this.error, this.width / 2, this.height - 34 + 1, 0xFF5555);
        }

        PackList source = this.dragSource;
        if (source != null && this.dragging) {
            this.renderDrag(source, mouseX, mouseY);
        } else if (this.menu != null) {
            this.menu.render(mouseX, mouseY);
        } else if (originalButton.isHovered()) {
            this.renderTooltip(I18n.translate("packed_packs.original_screen.info"), mouseX, mouseY);
        } else if (optionsButton.isHovered()) {
            this.renderTooltip(I18n.translate("packed_packs.options"), mouseX, mouseY);
        } else if (incompatibleButton.isHovered()) {
            this.renderTooltip(I18n.translate("packed_packs.hide_incompatible.info"), mouseX, mouseY);
        }
    }

    private void drawIcon(Identifier icon, int x, int y) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        this.minecraft.getTextureManager().bind(icon);
        drawTexture(x, y, 0.0F, 0.0F, 16, 16, 16.0F, 16.0F);
    }

    private void renderDrag(PackList source, int mouseX, int mouseY) {
        PackList target = this.dropTarget(mouseX, mouseY);
        if (target != null) {
            if (mouseY < target.y + 12) target.scroll(-6);
            if (mouseY > target.y + target.height - 12) target.scroll(6);
            if (target == source || target == this.selected && target.isRoot()) {
                int y = Math.clamp(target.rowTop(target.dropRow(mouseY)), target.y, target.y + target.height - 1);
                fill(target.x, y, target.x + target.width, y + 1, 0xFFFFFFFF);
            } else {
                fill(target.x, target.y, target.x + target.width, target.y + 1, 0xFFFFFFFF);
                fill(target.x, target.y + target.height - 1, target.x + target.width, target.y + target.height, 0xFFFFFFFF);
            }
        }
        List<Entry> packs = source.selected();
        if (!packs.isEmpty()) {
            String label = packs.size() == 1 ? packs.getFirst().name() : String.valueOf(packs.size());
            this.renderTooltip(label, mouseX, mouseY);
        }
    }

    private final class Menu {
        private static final int ITEM_HEIGHT = 12;
        private final List<String> labels = new ArrayList<>();
        private final List<@Nullable Runnable> actions = new ArrayList<>();
        private final int x, y;
        private int width;

        private Menu(int x, int y, int minWidth) {
            this.x = x;
            this.y = y;
            this.width = minWidth;
        }

        private void add(String label, @Nullable Runnable action) {
            this.labels.add(label);
            this.actions.add(action);
            this.width = Math.max(this.width, PackedPacksScreen.this.textRenderer.getWidth(label) + 8);
        }

        private int left() {
            return Math.max(0, Math.min(this.x, PackedPacksScreen.this.width - this.width));
        }

        private int top() {
            return Math.max(0, Math.min(this.y, PackedPacksScreen.this.height - this.labels.size() * ITEM_HEIGHT));
        }

        private int indexAt(int mouseX, int mouseY) {
            int index = Math.floorDiv(mouseY - this.top(), ITEM_HEIGHT);
            return mouseX >= this.left() && mouseX < this.left() + this.width && index >= 0 && index < this.labels.size() ? index : -1;
        }

        private void click(int mouseX, int mouseY) {
            int index = this.indexAt(mouseX, mouseY);
            Runnable action = index >= 0 ? this.actions.get(index) : null;
            if (action != null) action.run();
        }

        private void render(int mouseX, int mouseY) {
            int left = this.left();
            int top = this.top();
            int hovered = this.indexAt(mouseX, mouseY);
            fill(left - 1, top - 1, left + this.width + 1, top + this.labels.size() * ITEM_HEIGHT + 1, 0xFF808080);
            fill(left, top, left + this.width, top + this.labels.size() * ITEM_HEIGHT, 0xFF101010);
            for (int i = 0; i < this.labels.size(); i++) {
                int itemTop = top + i * ITEM_HEIGHT;
                boolean enabled = this.actions.get(i) != null;
                if (i == hovered && enabled) fill(left, itemTop, left + this.width, itemTop + ITEM_HEIGHT, 0xFF404040);
                PackedPacksScreen.this.textRenderer.drawWithShadow(this.labels.get(i), left + 4, itemTop + 2, enabled ? 0xFFFFFF : 0x707070);
            }
        }
    }
}
