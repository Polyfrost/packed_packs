package io.github.fishstiz.packed_packs.gui.components;

import io.github.fishstiz.packed_packs.PackedPacks;
import io.github.fishstiz.packed_packs.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class PackList {
    public static final int ROW_HEIGHT = 36;
    public static final int ARROW_RIGHT = 1, ARROW_LEFT = 2, ARROW_UP = 4, ARROW_DOWN = 8;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final Identifier HAMBURGER = new Identifier(PackedPacks.MOD_ID, "textures/gui/sprites/icon/hamburger.png");
    private static final Identifier ARROWS = new Identifier("textures/gui/resource_packs.png");
    private static final Identifier FOLDER_ICON = new Identifier(PackedPacks.MOD_ID, "textures/misc/unknown_folder.png");
    private final Minecraft minecraft = Minecraft.getInstance();
    private final boolean browsable;
    public final List<Entry> packs = new ArrayList<>();
    public final Set<Entry> selection = new HashSet<>();
    public Predicate<Entry> filter = entry -> true;
    public Function<Entry, List<Identifier>> badges = entry -> List.of();
    public Function<String, List<Entry>> children = folder -> List.of();
    public Function<Entry, Integer> arrows = entry -> 0;
    public Predicate<Entry> folderButton = entry -> false;
    public String folder = "";
    private boolean flat;
    private boolean reorderable;
    private List<Entry> view = List.of();
    public TextFieldWidget search;
    public int x, y, width, height;
    private int scroll;
    private int focus = -1;
    private int anchor = -1;

    public PackList(boolean browsable) {
        this.browsable = browsable;
    }

    public void layout(int x, int y, int width, int height) {
        String query = this.search != null ? this.search.getText() : "";
        this.search = new TextFieldWidget(0, this.minecraft.textRenderer, x + 1, y - 17, width - 2, 14);
        this.search.setMaxLength(64);
        this.search.setText(query);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.refresh();
    }

    public void refresh() {
        String query = this.search.getText().toLowerCase(Locale.ROOT);
        boolean opened = !this.folder.isEmpty();
        this.flat = !query.isEmpty() || !opened && !this.browsable;
        boolean locked = false;
        Stream<Entry> stream;
        if (this.flat) {
            stream = this.packs.stream().filter(entry -> entry.transferable() && entry.id.toLowerCase(Locale.ROOT).contains(query));
        } else if (opened) {
            List<Entry> children = this.children.apply(this.folder);
            Set<Entry> packs = new HashSet<>(this.packs);
            locked = children.stream().anyMatch(entry -> entry.nested);
            stream = children.stream().filter(entry -> entry.nested || (entry.transferable() ? packs.contains(entry) : this.hasContents(entry)));
        } else {
            stream = this.packs.stream().filter(entry -> entry.parent().isEmpty() && (entry.transferable() || this.hasContents(entry)));
        }
        stream = stream.filter(this.filter);
        Comparator<Entry> comparator = this.browsable && !locked ? Config.get().sort.comparator(entry -> entry.id, Entry::lastModified) : null;
        if (comparator != null) {
            stream = stream.sorted(Comparator.comparing((Entry entry) -> !entry.isFolder()).thenComparing(comparator));
        }
        List<Entry> view = new ArrayList<>(stream.toList());
        if (opened && !this.flat) view.addFirst(Entry.UP);
        this.view = view;
        this.reorderable = opened && !this.flat && comparator == null;
        this.selection.retainAll(this.view);
        this.focus = Math.min(this.focus, this.view.size() - 1);
        this.anchor = Math.min(this.anchor, this.view.size() - 1);
        this.scroll(0);
    }

    private boolean hasContents(Entry folder) {
        return this.packs.stream().anyMatch(entry -> entry.transferable() && entry.id.startsWith(folder.id) && this.filter.test(entry));
    }

    public List<Entry> view() {
        return this.view;
    }

    public List<Entry> rows() {
        return this.view.stream().filter(entry -> entry != Entry.UP).toList();
    }

    public boolean isRoot() {
        return this.folder.isEmpty();
    }

    public boolean reorderable() {
        return this.reorderable;
    }

    public List<Entry> selected() {
        return this.view.stream().filter(entry -> entry != Entry.UP && this.selection.contains(entry)).toList();
    }

    public @Nullable Entry focused() {
        return this.focus >= 0 && this.focus < this.view.size() ? this.view.get(this.focus) : null;
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
    }

    public int indexAt(int mouseY) {
        int index = Math.floorDiv(mouseY - this.y + this.scroll, ROW_HEIGHT);
        return index >= 0 && index < this.view.size() ? index : -1;
    }

    public int arrowAt(int mouseX, int mouseY) {
        int index = this.contains(mouseX, mouseY) ? this.indexAt(mouseY) : -1;
        int relX = mouseX - this.x - 2;
        int relY = mouseY - this.rowTop(index) - 2;
        if (index < 0 || relX < 0 || relX >= 32 || relY < 0 || relY >= 32) return 0;
        int arrows = this.arrows.apply(this.view.get(index));
        int side = arrows & (ARROW_RIGHT | ARROW_LEFT);
        if ((arrows & (ARROW_UP | ARROW_DOWN)) == 0) return side;
        return relX < 16 ? side : arrows & (relY < 16 ? ARROW_UP : ARROW_DOWN);
    }

    public boolean folderButtonAt(int mouseX, int mouseY) {
        int index = this.contains(mouseX, mouseY) ? this.indexAt(mouseY) : -1;
        int relX = mouseX - this.x - 36;
        int relY = mouseY - this.rowTop(index) - 22;
        return index >= 0 && relX >= 0 && relX < 12 && relY >= 0 && relY < 12 && this.folderButton.test(this.view.get(index));
    }

    private int thumbHeight() {
        return Math.max(16, this.height * this.height / Math.max(1, this.view.size() * ROW_HEIGHT));
    }

    public boolean overScrollbar(int mouseX, int mouseY) {
        return this.contains(mouseX, mouseY) && mouseX >= this.x + this.width - SCROLLBAR_WIDTH && this.view.size() * ROW_HEIGHT > this.height;
    }

    public void dragScrollbar(int mouseY) {
        int thumbHeight = this.thumbHeight();
        int range = this.view.size() * ROW_HEIGHT - this.height;
        if (range <= 0 || thumbHeight >= this.height) return;
        this.scroll((mouseY - this.y - thumbHeight / 2) * range / (this.height - thumbHeight) - this.scroll);
    }

    public int dropRow(int mouseY) {
        return Math.clamp(Math.floorDiv(mouseY - this.y + this.scroll + ROW_HEIGHT / 2, ROW_HEIGHT), 0, this.view.size());
    }

    public int dropIndex(int mouseY) {
        int row = this.dropRow(mouseY);
        return row < this.view.size() ? this.packs.indexOf(this.view.get(row)) : this.packs.size();
    }

    public int rowTop(int row) {
        return this.y + row * ROW_HEIGHT - this.scroll;
    }

    public void scroll(int amount) {
        int max = Math.max(0, this.view.size() * ROW_HEIGHT - this.height);
        this.scroll = Math.clamp(this.scroll + amount, 0, max);
    }

    public void click(int index, boolean toggle, boolean range) {
        if (index < 0) {
            if (!toggle && !range) this.selection.clear();
            return;
        }
        if (range && this.anchor >= 0) {
            this.selectRange(this.anchor, index);
        } else if (toggle) {
            Entry entry = this.view.get(index);
            if (!this.selection.remove(entry)) this.selection.add(entry);
            this.anchor = index;
        } else {
            this.selection.clear();
            this.selection.add(this.view.get(index));
            this.anchor = index;
        }
        this.focus = index;
    }

    public void setFocus(int index) {
        this.focus = index;
    }

    public void moveFocus(int delta, boolean range) {
        if (this.view.isEmpty()) return;
        int index = Math.clamp(this.focus + delta, 0, this.view.size() - 1);
        this.click(index, false, range);
        this.scroll(Math.min(0, index * ROW_HEIGHT - this.scroll) + Math.max(0, (index + 1) * ROW_HEIGHT - this.scroll - this.height));
    }

    public void selectAll() {
        this.selection.addAll(this.view);
    }

    private void selectRange(int from, int to) {
        this.selection.clear();
        this.selection.addAll(this.view.subList(Math.min(from, to), Math.max(from, to) + 1));
    }

    public void render(int mouseX, int mouseY, boolean active, String placeholder) {
        TextRenderer textRenderer = this.minecraft.textRenderer;
        this.search.render();
        if (this.search.getText().isEmpty() && !this.search.isFocused()) {
            String folder = this.folder.isEmpty() ? placeholder : this.folder;
            textRenderer.drawWithShadow(folder, this.search.x + 4, this.search.y + 3, 0x707070);
        }

        GuiElement.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0x80000000);
        int scale = new Window(this.minecraft).getScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(this.x * scale, this.minecraft.height - (this.y + this.height) * scale, this.width * scale, this.height * scale);

        boolean warn = !Config.get().hideIncompatibleWarnings;
        int rowWidth = this.width - SCROLLBAR_WIDTH;
        int textWidth = rowWidth - 32 - 6;
        int hovered = this.contains(mouseX, mouseY) && mouseX < this.x + rowWidth ? this.indexAt(mouseY) : -1;
        int first = this.scroll / ROW_HEIGHT;
        int last = Math.min(this.view.size(), (this.scroll + this.height) / ROW_HEIGHT + 1);
        for (int i = first; i < last; i++) {
            Entry entry = this.view.get(i);
            int top = this.y + i * ROW_HEIGHT - this.scroll;
            if (warn && entry.incompatible()) {
                GuiElement.fill(this.x, top, this.x + rowWidth, top + ROW_HEIGHT, 0xFF770000);
            }
            if (this.selection.contains(entry)) {
                GuiElement.fill(this.x, top, this.x + rowWidth, top + ROW_HEIGHT, active ? 0x60FFFFFF : 0x30FFFFFF);
            } else if (i == hovered) {
                GuiElement.fill(this.x, top, this.x + rowWidth, top + ROW_HEIGHT, 0x18FFFFFF);
            }
            if (active && i == this.focus) {
                GuiElement.fill(this.x, top, this.x + rowWidth, top + 1, 0xFFFFFFFF);
                GuiElement.fill(this.x, top + ROW_HEIGHT - 1, this.x + rowWidth, top + ROW_HEIGHT, 0xFFFFFFFF);
                GuiElement.fill(this.x, top, this.x + 1, top + ROW_HEIGHT, 0xFFFFFFFF);
                GuiElement.fill(this.x + rowWidth - 1, top, this.x + rowWidth, top + ROW_HEIGHT, 0xFFFFFFFF);
            }

            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            if (entry.pack != null) {
                entry.pack.bindIcon(this.minecraft.getTextureManager());
            } else {
                this.minecraft.getTextureManager().bind(FOLDER_ICON);
            }
            GuiElement.drawTexture(this.x + 2, top + 2, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
            int arrows = i == hovered ? this.arrows.apply(entry) : 0;
            if (arrows != 0) {
                int hit = this.arrowAt(mouseX, mouseY);
                GuiElement.fill(this.x + 2, top + 2, this.x + 34, top + 34, 0xA0909090);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.minecraft.getTextureManager().bind(ARROWS);
                int[] us = {0, 32, 96, 64};
                for (int arrow = 0; arrow < 4; arrow++) {
                    if ((arrows & 1 << arrow) == 0) continue;
                    GuiElement.drawTexture(this.x + 2, top + 2, us[arrow], hit == 1 << arrow ? 32.0F : 0.0F, 32, 32, 256.0F, 256.0F);
                }
            }

            List<Identifier> badges = this.badges.apply(entry);
            for (int badge = 0; badge < badges.size(); badge++) {
                this.minecraft.getTextureManager().bind(badges.get(badge));
                GuiElement.drawTexture(this.x + rowWidth - 18 - badge * 16, top + 2, 0.0F, 0.0F, 16, 16, 16.0F, 16.0F);
            }

            String name = this.flat ? entry.id : entry.name();
            int nameWidth = textWidth - badges.size() * 16;
            if (textRenderer.getWidth(name) > nameWidth) {
                name = textRenderer.trim(name, nameWidth - textRenderer.getWidth("...")) + "...";
            }
            String text = entry.description();
            if (warn && i == hovered && entry.incompatible()) {
                name = I18n.translate("resourcePack.incompatible");
                text = I18n.translate("resourcePack.incompatible." + entry.incompatibility());
            }
            textRenderer.drawWithShadow(name, this.x + 36, top + 3, 0xFFFFFF);
            List<String> description = textRenderer.split(text, textWidth);
            boolean button = this.folderButton.test(entry);
            for (int line = 0; line < (button ? 1 : 2) && line < description.size(); line++) {
                textRenderer.drawWithShadow(description.get(line), this.x + 36, top + (button ? 12 : 14) + 10 * line, 0x808080);
            }
            if (button) {
                boolean over = i == hovered && this.folderButtonAt(mouseX, mouseY);
                GuiElement.fill(this.x + 36, top + 22, this.x + 48, top + 34, over ? 0xFFFFFFFF : 0xFF808080);
                GuiElement.fill(this.x + 37, top + 23, this.x + 47, top + 33, 0xFF000000);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                this.minecraft.getTextureManager().bind(HAMBURGER);
                GuiElement.drawTexture(this.x + 37, top + 23, 0.0F, 0.0F, 10, 10, 10.0F, 10.0F);
            }
        }

        int contentHeight = this.view.size() * ROW_HEIGHT;
        if (contentHeight > this.height) {
            int thumbHeight = this.thumbHeight();
            int thumbTop = this.y + this.scroll * (this.height - thumbHeight) / (contentHeight - this.height);
            GuiElement.fill(this.x + rowWidth, this.y, this.x + this.width, this.y + this.height, 0xFF000000);
            GuiElement.fill(this.x + rowWidth, thumbTop, this.x + this.width, thumbTop + thumbHeight, 0xFFC0C0C0);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
