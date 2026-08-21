package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.forms.StructureForm;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 结构表单的渲染器：把 {@code .nbt} 结构文件里的方块逐个画出来。
 * <p>
 * 移植自 BBSTools 4.1。加载时解析结构文件的调色板与方块列表，之后缓存下来重复使用，
 * 只有换了文件才重新解析。渲染时按包围盒中心对齐，UI 预览还会按结构尺寸自动缩放到合适大小。
 * </p>
 */
public class StructureFormRenderer extends FormRenderer<StructureForm>
{
    private final List<BlockEntry> blocks = new ArrayList<>();

    private String lastFile;
    private BlockPos size = BlockPos.ORIGIN;
    private BlockPos boundsMin;
    private BlockPos boundsMax;

    public StructureFormRenderer(StructureForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureLoaded();

        context.batcher.getContext().draw();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        MatrixStack matrices = context.batcher.getContext().getMatrices();
        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();
        MatrixStackUtils.multiply(matrices, uiMatrix);

        float scale = this.getUiScale(x2 - x1, y2 - y1) * this.form.uiScale.get();

        matrices.scale(scale, scale, scale);
        this.center(matrices);

        // UI 里的法线矩阵需要翻转 Y，否则方块的明暗是反的
        matrices.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        matrices.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        consumers.setSubstitute(BBSRendering.getColorConsumer(this.form.color.get()));
        consumers.setUI(true);

        this.renderBlocks(matrices, consumers, 240, OverlayTexture.DEFAULT_UV);

        consumers.draw();
        consumers.setUI(false);
        consumers.setSubstitute(null);

        matrices.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureLoaded();

        if (this.blocks.isEmpty())
        {
            return;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.isPicking() ? 0 : context.light;

        context.stack.push();
        this.center(context.stack);

        if (context.isPicking())
        {
            // 拾取阶段改用 BBS 的拾取着色器，才能在 3D 视图里点中这个形态
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
            });
        }
        else
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) -> RenderSystem.enableBlend());
        }

        Color tint = this.form.color.get();

        consumers.setSubstitute(BBSRendering.getColorConsumer(tint));

        this.renderBlocks(context.stack, consumers, light, context.overlay);

        consumers.draw();
        consumers.setSubstitute(null);
        CustomVertexConsumerProvider.clearRunnables();

        context.stack.pop();

        RenderSystem.enableDepthTest();
    }

    private void renderBlocks(MatrixStack matrices, CustomVertexConsumerProvider consumers, int light, int overlay)
    {
        for (BlockEntry block : this.blocks)
        {
            matrices.push();
            matrices.translate(block.pos.getX(), block.pos.getY(), block.pos.getZ());

            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(block.state, matrices, consumers, light, overlay);

            matrices.pop();
        }
    }

    /** 把结构在水平方向上对齐到中心，竖直方向保持底部贴地 */
    private void center(MatrixStack matrices)
    {
        BlockPos min = this.boundsMin == null ? BlockPos.ORIGIN : this.boundsMin;
        BlockPos max = this.boundsMax == null ? this.size.add(-1, -1, -1) : this.boundsMax;
        float cx = (min.getX() + max.getX() + 1) / 2F;
        float cz = (min.getZ() + max.getZ() + 1) / 2F;

        matrices.translate(-cx, 0F, -cz);
    }

    /** UI 预览按结构最长边缩放，保证整体能落在预览框里 */
    private float getUiScale(int width, int height)
    {
        int w = 1;
        int h = 1;
        int d = 1;

        if (this.boundsMin != null && this.boundsMax != null)
        {
            w = Math.max(1, this.boundsMax.getX() - this.boundsMin.getX() + 1);
            h = Math.max(1, this.boundsMax.getY() - this.boundsMin.getY() + 1);
            d = Math.max(1, this.boundsMax.getZ() - this.boundsMin.getZ() + 1);
        }
        else if (this.size != null)
        {
            w = Math.max(1, this.size.getX());
            h = Math.max(1, this.size.getY());
            d = Math.max(1, this.size.getZ());
        }

        int max = Math.max(w, Math.max(h, d));

        return max <= 1 ? 1F : Math.min(1F, Math.min(width, height) * 0.9F / (height / 2.5F * max));
    }

    /** 结构文件没变就复用已解析的方块列表 */
    private void ensureLoaded()
    {
        String file = this.form.structureFile.get();

        if (file == null || file.isEmpty())
        {
            this.clear();

            return;
        }

        if (file.equals(this.lastFile) && !this.blocks.isEmpty())
        {
            return;
        }

        this.clear();
        this.lastFile = file;

        try
        {
            Link link = Link.create(file);
            File nbtFile = BBSMod.getProvider().getFile(link);

            if (nbtFile != null && nbtFile.exists())
            {
                this.parseStructure(NbtIo.readCompressed(nbtFile));

                return;
            }

            try (InputStream stream = BBSMod.getProvider().getAsset(link))
            {
                this.parseStructure(NbtIo.readCompressed(stream));
            }
        }
        catch (Exception ignored)
        {}
    }

    private void clear()
    {
        this.blocks.clear();

        this.size = BlockPos.ORIGIN;
        this.boundsMin = null;
        this.boundsMax = null;
        this.lastFile = null;
    }

    /** 解析原版结构文件格式：{@code size} + {@code palette} 调色板 + {@code blocks} 方块列表 */
    private void parseStructure(NbtCompound root)
    {
        if (root == null)
        {
            return;
        }

        if (root.contains("size", NbtElement.INT_ARRAY_TYPE))
        {
            int[] sz = root.getIntArray("size");

            if (sz.length >= 3)
            {
                this.size = new BlockPos(sz[0], sz[1], sz[2]);
            }
        }

        List<BlockState> paletteStates = new ArrayList<>();

        if (root.contains("palette", NbtElement.LIST_TYPE))
        {
            NbtList palette = root.getList("palette", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < palette.size(); i++)
            {
                paletteStates.add(this.readBlockState(palette.getCompound(i)));
            }
        }

        if (!root.contains("blocks", NbtElement.LIST_TYPE))
        {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        NbtList list = root.getList("blocks", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < list.size(); i++)
        {
            NbtCompound entry = list.getCompound(i);
            BlockPos pos = this.readBlockPos(entry.getList("pos", NbtElement.INT_TYPE));
            int stateIndex = entry.getInt("state");

            if (stateIndex < 0 || stateIndex >= paletteStates.size())
            {
                continue;
            }

            BlockState state = paletteStates.get(stateIndex);

            if (state == null || state.isAir())
            {
                continue;
            }

            this.blocks.add(new BlockEntry(state, pos));

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (!this.blocks.isEmpty())
        {
            this.boundsMin = new BlockPos(minX, minY, minZ);
            this.boundsMax = new BlockPos(maxX, maxY, maxZ);
        }
    }

    private BlockPos readBlockPos(NbtList list)
    {
        if (list == null || list.size() < 3)
        {
            return BlockPos.ORIGIN;
        }

        return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
    }

    private BlockState readBlockState(NbtCompound entry)
    {
        String name = entry.getString("Name");
        Block block;

        try
        {
            block = Registries.BLOCK.get(new Identifier(name));
        }
        catch (Exception e)
        {
            block = Blocks.AIR;
        }

        // 拼图方块在结构里只是标记，渲染出来会挡视线
        if (block == Blocks.JIGSAW || "minecraft:jigsaw".equals(name))
        {
            return Blocks.AIR.getDefaultState();
        }

        BlockState state = block.getDefaultState();

        if (entry.contains("Properties", NbtElement.COMPOUND_TYPE))
        {
            NbtCompound props = entry.getCompound("Properties");

            for (String key : props.getKeys())
            {
                Property<?> property = block.getStateManager().getProperty(key);

                if (property == null)
                {
                    continue;
                }

                Optional<?> parsed = property.parse(props.getString(key));

                if (parsed.isEmpty())
                {
                    continue;
                }

                try
                {
                    state = withProperty(state, property, parsed.get());
                }
                catch (Exception ignored)
                {}
            }
        }

        return state;
    }

    /** 泛型辅助：结构文件里读出的属性值是 {@code Object}，这里补回类型 */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, Object value)
    {
        return state.with(property, (T) value);
    }

    /**
     * 结构里的一个方块：状态 + 相对坐标
     */
    private record BlockEntry(BlockState state, BlockPos pos)
    {
    }
}
