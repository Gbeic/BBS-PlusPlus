package gbeic.bbsplusplus.client.structure;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 结构棒导出前的命名界面。
 * <p>
 * 移植自 BBSTools 4.1。默认名是当前时间戳，直接回车或点「保存」用输入框里的名字，
 * 点「默认保存」或按 ESC 关闭则用默认时间戳名保存。
 * </p>
 */
public class StructureStickSaveNameScreen extends Screen
{
    private static final int FIELD_WIDTH = 240;
    private static final int BUTTON_WIDTH = 100;

    private final String defaultName;
    private final Runnable saveDefault;
    private final Consumer<String> saveNamed;

    private TextFieldWidget nameField;
    private boolean saved;

    public StructureStickSaveNameScreen(String defaultName, Runnable saveDefault, Consumer<String> saveNamed)
    {
        super(Text.translatable("bbsplusplus.structure_stick.save_title"));

        this.defaultName = defaultName;
        this.saveDefault = saveDefault;
        this.saveNamed = saveNamed;
    }

    @Override
    protected void init()
    {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.nameField = new TextFieldWidget(
            this.textRenderer, centerX - FIELD_WIDTH / 2, centerY - 10, FIELD_WIDTH, 20,
            Text.translatable("bbsplusplus.structure_stick.save_name")
        );
        this.nameField.setMaxLength(96);
        this.nameField.setText(this.defaultName);

        this.addDrawableChild(this.nameField);
        this.setInitialFocus(this.nameField);
        this.nameField.setFocused(true);

        this.addDrawableChild(ButtonWidget
            .builder(Text.translatable("bbsplusplus.structure_stick.save"), (button) -> this.saveAndClose(false))
            .dimensions(centerX - BUTTON_WIDTH - 5, centerY + 24, BUTTON_WIDTH, 20)
            .build());

        this.addDrawableChild(ButtonWidget
            .builder(Text.translatable("bbsplusplus.structure_stick.save_default"), (button) -> this.saveAndClose(true))
            .dimensions(centerX + 5, centerY + 24, BUTTON_WIDTH, 20)
            .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
        {
            this.saveAndClose(false);

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 直接关闭界面（ESC）等同于用默认名保存 */
    @Override
    public void close()
    {
        this.saveAndClose(true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.renderBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 54, 0xFFFFFF);
        context.drawTextWithShadow(
            this.textRenderer, Text.translatable("bbsplusplus.structure_stick.save_name"),
            centerX - FIELD_WIDTH / 2, centerY - 24, 0xA0A0A0
        );

        super.render(context, mouseX, mouseY, delta);
    }

    private void saveAndClose(boolean useDefault)
    {
        // 回车与按钮点击可能同一帧触发两次，这里保证只保存一次
        if (this.saved)
        {
            return;
        }

        this.saved = true;

        if (useDefault)
        {
            this.saveDefault.run();
        }
        else
        {
            this.saveNamed.accept(this.nameField == null ? this.defaultName : this.nameField.getText());
        }

        if (this.client != null)
        {
            this.client.setScreen(null);
        }
    }
}
