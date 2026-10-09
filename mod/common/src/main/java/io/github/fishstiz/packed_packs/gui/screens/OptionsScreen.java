package io.github.fishstiz.packed_packs.gui.screens;

import io.github.fishstiz.packed_packs.Folders;
import io.github.fishstiz.packed_packs.config.Config;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class OptionsScreen extends Screen {
    private static final String DEV_MODE = "packed_packs.dev_mode";
    private final Screen parent;
    private final Config config = Config.get();
    private final List<Option> options = List.of(
            new Option("packed_packs.options.replace_screen", () -> this.config.replaceOriginal, value -> this.config.replaceOriginal = value),
            new Option("packed_packs.options.apply_on_close", () -> this.config.applyOnClose, value -> this.config.applyOnClose = value),
            new Option("packed_packs.options.hide_incompatible_warnings", () -> this.config.hideIncompatibleWarnings, value -> this.config.hideIncompatibleWarnings = value),
            new Option("packed_packs.options.folders_module_by_default", () -> this.config.foldersModuleByDefault, value -> this.config.foldersModuleByDefault = value),
            new Option("packed_packs.options.remember_last_viewed_profile", () -> this.config.rememberLastViewedProfile, value -> this.config.rememberLastViewedProfile = value),
            new Option(DEV_MODE, () -> this.config.devMode, value -> this.config.devMode = value)
    );

    private record Option(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        private String label() {
            String state = I18n.translate(this.getter.getAsBoolean() ? "options.on" : "options.off");
            return this.key.equals(DEV_MODE) ? I18n.translate(this.key, state) : I18n.translate(this.key) + ": " + state;
        }
    }

    OptionsScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        int top = Math.max(24, this.height / 2 - (this.options.size() + 1) * 12);
        for (int i = 0; i < this.options.size(); i++) {
            this.buttons.add(new ButtonWidget(i, this.width / 2 - 120, top + i * 24, 240, 20, this.options.get(i).label()));
        }
        this.buttons.add(new ButtonWidget(this.options.size(), this.width / 2 - 120, top + this.options.size() * 24 + 4, 240, 20, I18n.translate("gui.done")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id < this.options.size()) {
            Option option = this.options.get(button.id);
            option.setter.accept(!option.getter.getAsBoolean());
            button.message = option.label();
        } else {
            this.config.save();
            Folders.expire();
            this.minecraft.openScreen(this.parent);
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            this.buttonClicked(this.buttons.get(this.options.size()));
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, I18n.translate("packed_packs.options.title"), this.width / 2, 8, 0xFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
    }
}
