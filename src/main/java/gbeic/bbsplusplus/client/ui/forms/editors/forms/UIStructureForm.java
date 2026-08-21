package gbeic.bbsplusplus.client.ui.forms.editors.forms;

import gbeic.bbsplusplus.forms.StructureForm;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 结构表单的编辑界面。
 * <p>
 * 移植自 BBSTools 4.1，界面文案改为走语言文件。
 * 提供结构文件选择、手填路径和整体染色；结构文件列表直接扫描 BBS 资源目录下的 {@code structures} 文件夹，
 * 也就是结构棒导出的位置。
 * </p>
 */
public class UIStructureForm extends UIForm<StructureForm>
{
    public UIStructureForm()
    {
        super();

        this.defaultPanel = new UIStructureFormPanel(this);
        this.registerPanel(this.defaultPanel, L10n.lang("bbs.ui.forms.structure"), Icons.STRUCTURE);
        this.registerDefaultPanels();
    }

    public static class UIStructureFormPanel extends UIFormPanel<StructureForm>
    {
        /** 结构文件所在的资源子目录 */
        private static final String STRUCTURES_FOLDER = "structures/";

        public final UIColor color;
        public final UIButton pickStructure;
        public final UITextbox structureFile;

        public UIStructureFormPanel(UIForm<StructureForm> editor)
        {
            super(editor);

            this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
            this.pickStructure = new UIButton(label("bbs.ui.forms.structure.pick"), (b) -> this.pickStructure());
            this.structureFile = new UITextbox(100, (s) -> this.form.structureFile.set(s)).path().border();

            this.options.add(
                this.color,
                this.pickStructure,
                UI.label(label("bbs.ui.forms.structure.file")).marginTop(6),
                this.structureFile
            );
        }

        @Override
        public void startEdit(StructureForm form)
        {
            super.startEdit(form);

            this.color.setColor(form.color.get().getARGBColor());
            this.structureFile.setText(form.structureFile.get());
        }

        private void pickStructure()
        {
            UIStringOverlayPanel overlay = new UIStringOverlayPanel(
                label("bbs.ui.forms.structure.pick"),
                true,
                getSavedStructures(),
                (value) ->
                {
                    String path = value == null || value.isEmpty() ? "" : STRUCTURES_FOLDER + value;

                    this.form.structureFile.set(path);
                    this.structureFile.setText(path);
                }
            );

            String current = this.form.structureFile.get();

            if (current != null && current.startsWith(STRUCTURES_FOLDER))
            {
                overlay.set(current.substring(STRUCTURES_FOLDER.length()));
            }

            UIOverlay.addOverlay(this.getContext(), overlay, 280, 0.5F);
        }

        /** 递归扫描资源目录下所有 {@code .nbt} 结构文件，返回相对路径 */
        private static List<String> getSavedStructures()
        {
            List<String> structures = new ArrayList<>();
            File folder = BBSMod.getAssetsPath("structures");

            if (!folder.exists() || !folder.isDirectory())
            {
                return structures;
            }

            Path root = folder.toPath();

            try (Stream<Path> stream = Files.walk(root))
            {
                stream
                    .filter(Files::isRegularFile)
                    .filter((path) -> path.toString().toLowerCase().endsWith(".nbt"))
                    .forEach((path) -> structures.add(root.relativize(path).toString().replace('\\', '/')));
            }
            catch (Exception ignored)
            {}

            return structures;
        }

        private static IKey label(String key)
        {
            return L10n.lang(key);
        }
    }
}
