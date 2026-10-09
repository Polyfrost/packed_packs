package io.github.fishstiz.packed_packs.gui.screens;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;

final class TextPromptScreen extends Screen {
    private final Screen parent;
    private final String title;
    private final Consumer<String> onDone;
    private final boolean allowEmpty;
    private String text;
    private TextFieldWidget field;

    TextPromptScreen(Screen parent, String title, String initial, Consumer<String> onDone) {
        this(parent, title, initial, false, onDone);
    }

    TextPromptScreen(Screen parent, String title, String initial, boolean allowEmpty, Consumer<String> onDone) {
        this.allowEmpty = allowEmpty;
        this.parent = parent;
        this.title = title;
        this.text = initial;
        this.onDone = onDone;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.field = new TextFieldWidget(0, this.textRenderer, this.width / 2 - 100, this.height / 2 - 20, 200, 20);
        this.field.setMaxLength(512);
        this.field.setText(this.text);
        this.field.setFocused(true);
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(0, this.width / 2 - 100, this.height / 2 + 8, 98, 20, I18n.translate("gui.done")));
        this.buttons.add(new ButtonWidget(1, this.width / 2 + 2, this.height / 2 + 8, 98, 20, I18n.translate("gui.cancel")));
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        String result = this.field.getText().trim();
        if (button.id == 0 && (this.allowEmpty || !result.isEmpty())) {
            this.onDone.accept(result);
        }
        this.minecraft.openScreen(this.parent);
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.buttonClicked(this.buttons.get(0));
        } else if (key == Keyboard.KEY_ESCAPE) {
            this.buttonClicked(this.buttons.get(1));
        } else {
            this.field.keyPressed(chr, key);
            this.text = this.field.getText();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        this.field.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        this.field.tick();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        this.field.render();
        super.render(mouseX, mouseY, tickDelta);
    }
}
