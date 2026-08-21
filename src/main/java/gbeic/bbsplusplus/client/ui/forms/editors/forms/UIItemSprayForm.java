package gbeic.bbsplusplus.client.ui.forms.editors.forms;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import gbeic.bbsplusplus.forms.ItemSprayForm;

import java.util.List;
/**
 * 物品喷射表单的UI编辑器
 */
public class UIItemSprayForm extends UIForm<ItemSprayForm>
{
    public UIItemSprayForm()
    {
        super();
        this.defaultPanel = new UIItemSprayFormPanel(this);
        this.registerPanel(this.defaultPanel, L10n.lang("bbs.ui.forms.item_spray"), Icons.SPHERE);
        this.registerDefaultPanels();
    }

    public static class UIItemSprayFormPanel extends UIFormPanel<ItemSprayForm>
    {
        public UITrackpad amount;
        public UITrackpad range;
        public UICirculate emissionShape;
        public UITrackpad radius;
        public UITrackpad spawnWidth;
        public UITrackpad spawnHeight;
        public UITrackpad spawnOffset;
        public UIToggle stopAtCenter;
        public UITrackpad scatter;
        public UITrackpad speed;
        public UITrackpad speedOffset;
        public UIToggle gravity;
        public UITrackpad gravitySpeed;
        public UIToggle collision;
        public UITrackpad frequency;
        public UITrackpad lifetime;
        public UIToggle previewMode;
        public UITrackpad simulationTime;
        public UITrackpad seed;
        public UITrackpad itemPitch;
        public UITrackpad itemYaw;
        public UITrackpad itemRoll;
        public UITrackpad rotationSpeedX;
        public UITrackpad rotationSpeedY;
        public UITrackpad rotationSpeedZ;
        public UITrackpad rotationRandomSpeed;
        public UIToggle billboard;
        public UITrackpad itemScale;
        public UITrackpad scaleScatter;
        public UITrackpad scaleInTime;
        public UIToggle showGuide;
        public UIColor color;
        
        public UIRenderable itemsLabel;
        public UIButton addItem;
        // 存储物品编辑器的滚动列表可以放在 options 里面，或者单独管理
        
        public UIItemSprayFormPanel(UIForm<ItemSprayForm> editor)
        {
            super(editor);
            
            this.amount = new UITrackpad((v) -> this.form.amount.set(v.intValue())).integer().limit(1);
            this.range = new UITrackpad((v) -> this.form.range.set(v.floatValue()));
            this.emissionShape = new UICirculate((b) ->
            {
                this.form.emissionShape.set(b.getValue());
                this.fillItems();
            });
            this.emissionShape.addLabel(label("bbs.ui.forms.item_spray.shape.cone"));
            this.emissionShape.addLabel(label("bbs.ui.forms.item_spray.shape.plane"));
            this.emissionShape.addLabel(label("bbs.ui.forms.item_spray.shape.sphere_out"));
            this.emissionShape.addLabel(label("bbs.ui.forms.item_spray.shape.sphere_in"));
            this.radius = new UITrackpad((v) -> this.form.radius.set(v.floatValue()));
            this.spawnWidth = new UITrackpad((v) -> this.form.spawnWidth.set(v.floatValue())).limit(0);
            this.spawnHeight = new UITrackpad((v) -> this.form.spawnHeight.set(v.floatValue())).limit(0);
            this.spawnOffset = new UITrackpad((v) -> this.form.spawnOffset.set(v.floatValue())).limit(0);
            this.stopAtCenter = new UIToggle(label("bbs.ui.forms.item_spray.stop_at_center"), false, (b) -> this.form.stopAtCenter.set(b.getValue()));
            this.scatter = new UITrackpad((v) -> this.form.scatter.set(v.floatValue()));
            this.speed = new UITrackpad((v) -> this.form.speed.set(v.floatValue()));
            this.speedOffset = new UITrackpad((v) -> this.form.speedOffset.set(v.floatValue()));
            this.gravity = new UIToggle(label("bbs.ui.forms.item_spray.gravity"), false, (b) -> this.form.gravity.set(b.getValue()));
            this.gravitySpeed = new UITrackpad((v) -> this.form.gravitySpeed.set(v.floatValue())).limit(0);
            this.collision = new UIToggle(label("bbs.ui.forms.item_spray.collision"), false, (b) -> this.form.collision.set(b.getValue()));
            this.frequency = new UITrackpad((v) -> this.form.frequency.set(v.intValue())).integer().limit(1);
            this.lifetime = new UITrackpad((v) -> this.form.lifetime.set(v.intValue())).integer().limit(0);
            this.previewMode = new UIToggle(label("bbs.ui.forms.item_spray.preview_mode"), false, (b) -> this.form.previewMode.set(b.getValue()));
            this.simulationTime = new UITrackpad((v) -> this.form.simulationTime.set(v.floatValue())).limit(0);
            this.seed = new UITrackpad((v) -> this.form.seed.set(v.intValue())).integer();
            this.itemPitch = new UITrackpad((v) -> this.form.itemPitch.set(v.floatValue()));
            this.itemYaw = new UITrackpad((v) -> this.form.itemYaw.set(v.floatValue()));
            this.itemRoll = new UITrackpad((v) -> this.form.itemRoll.set(v.floatValue()));
            this.rotationSpeedX = new UITrackpad((v) -> this.form.rotationSpeedX.set(v.floatValue()));
            this.rotationSpeedY = new UITrackpad((v) -> this.form.rotationSpeedY.set(v.floatValue()));
            this.rotationSpeedZ = new UITrackpad((v) -> this.form.rotationSpeedZ.set(v.floatValue()));
            this.rotationRandomSpeed = new UITrackpad((v) -> this.form.rotationRandomSpeed.set(v.floatValue())).limit(0);
            this.billboard = new UIToggle(label("bbs.ui.forms.item_spray.billboard"), false, (b) -> this.form.billboard.set(b.getValue()));
            this.itemScale = new UITrackpad((v) -> this.form.itemScale.set(v.floatValue())).limit(0);
            this.scaleScatter = new UITrackpad((v) -> this.form.scaleScatter.set(v.floatValue())).limit(0);
            this.scaleInTime = new UITrackpad((v) -> this.form.scaleInTime.set(v.intValue())).integer().limit(0);
            
            this.showGuide = new UIToggle(label("bbs.ui.forms.item_spray.show_guide"), false, (b) -> this.form.showGuide.set(b.getValue()));
            this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
            
            this.addItem = new UIButton(label("bbs.ui.forms.item_spray.add_item"), (b) -> 
            {
                this.form.items.add(new ValueItemStack(String.valueOf(this.form.items.getList().size())));
                this.fillItems();
            });

            this.addOptions(false);
        }

        @Override
        public void startEdit(ItemSprayForm form)
        {
            super.startEdit(form);
            
            this.amount.setValue(form.amount.get());
            this.range.setValue(form.range.get());
            this.emissionShape.setValue(form.emissionShape.get());
            this.radius.setValue(form.radius.get());
            this.spawnWidth.setValue(form.spawnWidth.get());
            this.spawnHeight.setValue(form.spawnHeight.get());
            this.spawnOffset.setValue(form.spawnOffset.get());
            this.stopAtCenter.setValue(form.stopAtCenter.get());
            this.scatter.setValue(form.scatter.get());
            this.speed.setValue(form.speed.get());
            this.speedOffset.setValue(form.speedOffset.get());
            this.gravity.setValue(form.gravity.get());
            this.gravitySpeed.setValue(form.gravitySpeed.get());
            this.collision.setValue(form.collision.get());
            this.frequency.setValue(form.frequency.get());
            this.lifetime.setValue(form.lifetime.get());
            this.previewMode.setValue(form.previewMode.get());
            this.simulationTime.setValue(form.simulationTime.get());
            this.seed.setValue(form.seed.get());
            this.itemPitch.setValue(form.itemPitch.get());
            this.itemYaw.setValue(form.itemYaw.get());
            this.itemRoll.setValue(form.itemRoll.get());
            this.rotationSpeedX.setValue(form.rotationSpeedX.get());
            this.rotationSpeedY.setValue(form.rotationSpeedY.get());
            this.rotationSpeedZ.setValue(form.rotationSpeedZ.get());
            this.rotationRandomSpeed.setValue(form.rotationRandomSpeed.get());
            this.billboard.setValue(form.billboard.get());
            this.itemScale.setValue(form.itemScale.get());
            this.scaleScatter.setValue(form.scaleScatter.get());
            this.scaleInTime.setValue(form.scaleInTime.get());
            
            this.showGuide.setValue(form.showGuide.get());
            this.color.setColor(form.color.get().getARGBColor());
            
            this.fillItems();
        }
        
        private void fillItems()
        {
            // 每次填入前，先清空动态生成的物品编辑器
            this.options.removeAll();

            this.addOptions(true);

            if (this.options.getParent() != null)
            {
                this.options.getParent().resize();
            }
        }

        private void addOptions(boolean includeItems)
        {
            this.options.add(section("bbs.ui.forms.item_spray.section.items"));
            this.options.add(this.addItem);

            if (includeItems)
            {
                this.addItemEditors();
            }

            this.options.add(section("bbs.ui.forms.item_spray.section.emission"));
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.amount")), this.amount);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.frequency")), this.frequency);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.lifetime")), this.lifetime);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.range")), this.range);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.emission_shape")), this.emissionShape);
            this.addShapeOptions();
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.speed")), this.speed);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.speed_offset")), this.speedOffset);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.scatter")), this.scatter);

            this.options.add(section("bbs.ui.forms.item_spray.section.physics_preview"));
            this.options.add(this.gravity, this.collision);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.gravity_speed")), this.gravitySpeed);
            this.options.add(this.previewMode);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.simulation_time")), this.simulationTime);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.seed")), this.seed);

            this.options.add(section("bbs.ui.forms.item_spray.section.rotation"));
            this.options.add(this.billboard);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.item_pitch")), this.itemPitch);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.item_yaw")), this.itemYaw);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.item_roll")), this.itemRoll);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.rotation_speed_x")), this.rotationSpeedX);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.rotation_speed_y")), this.rotationSpeedY);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.rotation_speed_z")), this.rotationSpeedZ);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.rotation_random_speed")), this.rotationRandomSpeed);

            this.options.add(section("bbs.ui.forms.item_spray.section.display"));
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.item_scale")), this.itemScale);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.scale_scatter")), this.scaleScatter);
            this.options.add(UI.label(label("bbs.ui.forms.item_spray.scale_in_time")), this.scaleInTime);
            this.options.add(this.showGuide, this.color);
        }

        private void addItemEditors()
        {
            List<ValueItemStack> list = this.form.items.getList();

            for (int i = 0; i < list.size(); i++)
            {
                final int index = i;
                ValueItemStack stackValue = list.get(i);
                
                UIItemStack stackEditor = new UIItemStack((stack) -> {
                    stackValue.set(stack.copy());
                });
                stackEditor.setStack(stackValue.get());
                
                UIIcon removeBtn = new UIIcon(Icons.REMOVE, (b) -> {
                    this.form.items.getAllTyped().remove(index);
                    this.form.items.sync();
                    this.fillItems();
                });
                
                this.options.add(UI.row(stackEditor, removeBtn));
            }
        }

        private void addShapeOptions()
        {
            int shape = this.getActiveEmissionShape();

            if (shape == ItemSprayForm.SHAPE_PLANE)
            {
                this.options.add(UI.label(label("bbs.ui.forms.item_spray.spawn_width")), this.spawnWidth);
                this.options.add(UI.label(label("bbs.ui.forms.item_spray.spawn_height")), this.spawnHeight);
            }
            else if (shape == ItemSprayForm.SHAPE_SPHERE_OUT)
            {
                this.options.add(UI.label(label("bbs.ui.forms.item_spray.spawn_offset")), this.spawnOffset);
            }
            else if (shape == ItemSprayForm.SHAPE_SPHERE_IN)
            {
                this.options.add(UI.label(label("bbs.ui.forms.item_spray.spawn_offset")), this.spawnOffset);
                this.options.add(this.stopAtCenter);
            }
            else
            {
                this.options.add(UI.label(label("bbs.ui.forms.item_spray.radius")), this.radius);
            }
        }

        private int getActiveEmissionShape()
        {
            int shape = this.form == null ? this.emissionShape.getValue() : this.form.emissionShape.get();

            return Math.max(ItemSprayForm.SHAPE_CONE, Math.min(ItemSprayForm.SHAPE_COUNT - 1, shape));
        }

        private static IKey label(String key)
        {
            return L10n.lang(key);
        }

        private static UIElement section(String key)
        {
            return UI.label(label(key)).background().marginTop(UIConstants.SECTION_GAP);
        }
    }
}
