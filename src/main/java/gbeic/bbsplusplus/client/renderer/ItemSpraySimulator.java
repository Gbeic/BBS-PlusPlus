package gbeic.bbsplusplus.client.renderer;

import gbeic.bbsplusplus.forms.ItemSprayForm;
import gbeic.bbsplusplus.mixin.GameRendererAccessor;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 物品喷射的确定性模拟器。
 *
 * 在确定性预览模式下，按模拟时间与种子直接采样整段喷射历史，并把结果发布为真实世界快照，
 * 让预览粒子脱离源模型方块裁剪继续显示。同时负责世界/预览矩阵的反推与稳定方块变换计算，
 * 保证确定性粒子在相机移动、Iris 视野摇晃等情况下不抖动。
 */
class ItemSpraySimulator
{
    private static final int MAX_PREVIEW_ITEMS = 2048;

    private final ItemSprayFormRenderer owner;
    private final ItemSprayEmitter emitter;
    private final ItemSprayForm form;
    private final List<SprayedItem> deterministicItems = new ArrayList<>();

    ItemSpraySimulator(ItemSprayFormRenderer owner, ItemSprayEmitter emitter, ItemSprayForm form)
    {
        this.owner = owner;
        this.emitter = emitter;
        this.form = form;
    }

    List<SprayedItem> getDeterministicItems()
    {
        return this.deterministicItems;
    }

    /**
     * 按当前模拟时间采样确定性粒子：从存活窗口的起始发射刻开始，逐刻逐数量生成粒子并重放其运动。
     */
    void sampleDeterministicItems(FormRenderingContext context)
    {
        this.deterministicItems.clear();

        float time = this.getDeterministicSimulationTime(context);

        int frequency = Math.max(1, this.form.frequency.get());
        int amount = Math.max(1, this.form.amount.get());
        float speed = this.form.speed.get();
        float range = this.form.range.get();
        int maxAge = this.getMaxAge(speed, range);
        List<ItemStack> items = this.getItemStacks();
        World world = this.getPreviewWorld(context);
        World collisionWorld = this.usesWorldCollision(context) ? world : null;
        Matrix4f previewTransform = this.getDeterministicMotionTransform(context);
        Vector3d motionWorldOffset = this.getDeterministicMotionWorldOffset(context);
        Matrix3f gravityTransform = previewTransform.get3x3(new Matrix3f());
        Vector3f gravityLocal = new Vector3f(0F, -this.form.gravitySpeed.get(), 0F);

        if (this.form.gravity.get())
        {
            gravityTransform.invert();
            gravityTransform.transform(gravityLocal);
        }
        else
        {
            gravityLocal.set(0F, 0F, 0F);
        }

        if (maxAge <= 0 || items.isEmpty())
        {
            return;
        }

        int endTick = (int) Math.floor(time);
        int startTick = Math.max(0, endTick - maxAge + 1);
        startTick += Math.floorMod(frequency - Math.floorMod(startTick, frequency), frequency);

        Vector3d origin = new Vector3d();
        Matrix3f identity = new Matrix3f();

        for (int spawnTick = startTick; spawnTick <= endTick; spawnTick += frequency)
        {
            float age = time - spawnTick;
            if (age < 0F || age >= maxAge)
            {
                continue;
            }

            for (int index = 0; index < amount; index++)
            {
                if (this.deterministicItems.size() >= MAX_PREVIEW_ITEMS)
                {
                    return;
                }

                this.deterministicItems.add(this.createDeterministicItem(items, collisionWorld, previewTransform, motionWorldOffset, gravityLocal, origin, identity, spawnTick, index, age, maxAge));
            }
        }
    }

    /**
     * 把当前确定性采样结果发布为真实世界快照，供全局世界渲染阶段绘制。
     * 快照粒子携带源方块信息，源方块被破坏时会在下次全局更新中移除。
     */
    void publishDeterministicWorldItems(FormRenderingContext context)
    {
        ItemSpraySource source = ItemSprayGlobalSystem.getSource(context.entity);

        if (source == null)
        {
            return;
        }

        this.prepareDeterministicWorldPublish(source);
        ItemSprayGlobalSystem.clearOwnedDeterministicWorldItems(this.owner);

        if (this.deterministicItems.isEmpty())
        {
            return;
        }

        Matrix4f modelMatrix = this.getDeterministicMotionTransform(context);
        Vector3d worldOffset = this.getDeterministicMotionWorldOffset(context);
        Matrix3f renderRotation = modelMatrix.get3x3(new Matrix3f());

        for (SprayedItem item : this.deterministicItems)
        {
            Vector3d pos = ItemSprayPhysics.transformPosition(modelMatrix, worldOffset, item.pos);
            Vector3d prevPos = ItemSprayPhysics.transformPosition(modelMatrix, worldOffset, item.prevPos);
            SprayedItem copy = new SprayedItem(
                this.owner,
                item.stack.copy(),
                pos,
                new Vector3d(item.velocity),
                new Vector3f(item.initialRotation),
                new Vector3f(item.rotationSpeed),
                item.maxAge,
                item.useGravity,
                item.gravitySpeed,
                item.useCollision,
                item.billboard,
                item.scale,
                item.scaleInTime,
                item.color,
                source
            );

            copy.prevPos.set(prevPos);
            copy.rotation.set(item.rotation);
            copy.prevRotation.set(item.prevRotation);
            copy.age = item.age;
            copy.renderAge = item.renderAge;
            copy.stopped = item.stopped;
            copy.renderRotation = new Matrix3f(renderRotation);

            ItemSprayGlobalSystem.DETERMINISTIC_WORLD_ITEMS.add(copy);
        }
    }

    /**
     * 每个世界渲染帧只清一次该源的旧快照，避免同一模型内多个喷射形态互相覆盖。
     */
    private void prepareDeterministicWorldPublish(ItemSpraySource source)
    {
        Long clearedFrame = ItemSprayGlobalSystem.DETERMINISTIC_SOURCE_CLEAR_FRAMES.get(source);

        if (clearedFrame == null || clearedFrame != ItemSprayGlobalSystem.currentWorldRenderFrame)
        {
            ItemSprayGlobalSystem.clearDeterministicWorldItems(source);
            ItemSprayGlobalSystem.DETERMINISTIC_SOURCE_CLEAR_FRAMES.put(source, ItemSprayGlobalSystem.currentWorldRenderFrame);
        }
    }

    private float getDeterministicSimulationTime(FormRenderingContext context)
    {
        return Math.max(0F, this.form.simulationTime.get());
    }

    World getPreviewWorld(FormRenderingContext context)
    {
        if (context.entity != null && context.entity.getWorld() != null)
        {
            return context.entity.getWorld();
        }

        return MinecraftClient.getInstance().world;
    }

    private Matrix4f getPreviewTransform(FormRenderingContext context)
    {
        if (context.world != null)
        {
            return new Matrix4f(context.world.peek().getPositionMatrix());
        }

        return new Matrix4f();
    }

    /**
     * 取得确定性运动使用的局部-世界变换：世界模型方块模式用稳定方块矩阵，否则用当前预览矩阵。
     */
    private Matrix4f getDeterministicMotionTransform(FormRenderingContext context)
    {
        if (this.usesWorldModelBlockTransform(context))
        {
            ItemSpraySource source = ItemSprayGlobalSystem.getSource(context.entity);
            Matrix4f stableMatrix = this.getStableWorldModelBlockMatrix(context, source);

            if (stableMatrix != null)
            {
                return stableMatrix;
            }

            return this.getWorldModelMatrix(context);
        }

        return this.getPreviewTransform(context);
    }

    /**
     * 取得确定性运动的世界偏移：稳定方块模式下用方块整数坐标保持双精度，否则返回 null 表示使用相机位置。
     */
    private Vector3d getDeterministicMotionWorldOffset(FormRenderingContext context)
    {
        if (this.usesWorldModelBlockTransform(context))
        {
            ItemSpraySource source = ItemSprayGlobalSystem.getSource(context.entity);

            if (this.getStableWorldModelBlock(context, source) != null)
            {
                return new Vector3d(source.pos().getX(), source.pos().getY(), source.pos().getZ());
            }

            return context.camera.position;
        }

        return null;
    }

    private Matrix4f getStableWorldModelBlockMatrix(FormRenderingContext context, ItemSpraySource source)
    {
        ModelBlockEntity modelBlock = this.getStableWorldModelBlock(context, source);

        if (modelBlock == null)
        {
            return null;
        }

        MatrixStack stack = new MatrixStack();

        /* 默认模式会每帧重放碰撞。这里用方块整数坐标作为双精度世界偏移，
         * 矩阵只保留小范围局部变换，避免玩家移动视角时相机相对坐标的 float 误差让已碰撞粒子轻微抖动。 */
        stack.translate(0.5F, 0F, 0.5F);
        MatrixStackUtils.applyTransform(stack, modelBlock.getProperties().getTransform());
        stack.peek().getPositionMatrix().mul(context.world.peek().getPositionMatrix());

        return new Matrix4f(stack.peek().getPositionMatrix());
    }

    private ModelBlockEntity getStableWorldModelBlock(FormRenderingContext context, ItemSpraySource source)
    {
        if (source == null || context.world == null)
        {
            return null;
        }

        ModelBlockEntity modelBlock = source.getModelBlock(this.getPreviewWorld(context));

        if (modelBlock == null || modelBlock.getProperties() == null)
        {
            return null;
        }

        // 看向玩家模式本身会跟随相机方向变化，继续走当前渲染栈，避免错误冻结模型方块朝向。
        return modelBlock.getProperties().isLookAt() ? null : modelBlock;
    }

    private boolean usesWorldModelBlockTransform(FormRenderingContext context)
    {
        return ItemSprayGlobalSystem.isMainWorldRenderPass()
            && context.type == FormRenderType.MODEL_BLOCK
            && !context.modelRenderer
            && !context.ui;
    }

    private boolean usesWorldCollision(FormRenderingContext context)
    {
        return this.form.collision.get()
            && ItemSprayGlobalSystem.isMainWorldRenderPass()
            && context.type != FormRenderType.PREVIEW
            && !context.modelRenderer
            && !context.ui;
    }

    private Matrix4f getWorldModelMatrix(FormRenderingContext context)
    {
        Matrix4f modelMatrix = this.getWorldViewInverseMatrix(context);

        modelMatrix.mul(context.stack.peek().getPositionMatrix());

        return modelMatrix;
    }

    /**
     * 反推当前世界渲染栈对应的视图逆矩阵，把模型空间坐标还原到世界空间。
     * Iris 开启光影且开启视野摇晃时，需要剥掉转移到 model-view 矩阵上的摇晃。
     */
    Matrix4f getWorldViewInverseMatrix(FormRenderingContext context)
    {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        if (camera == null)
        {
            return new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix());
        }

        Matrix4f matrix = createCleanWorldViewMatrix(camera);

        if (this.shouldStripIrisViewBobbing())
        {
            /* Iris 为了兼容光影包，会把视野摇晃从投影矩阵转移到世界渲染的 model-view 矩阵。
             * BBS 在这个矩阵上继续渲染模型方块，因此这里反推世界坐标时必须把同一份 bobView 剥掉。 */
            Matrix4f bobbing = this.createViewBobbingMatrix(context.getTransition());

            bobbing.mul(matrix);
            matrix = bobbing;
        }

        matrix.invert();

        return matrix;
    }

    private static Matrix4f createCleanWorldViewMatrix(Camera camera)
    {
        Matrix4f matrix = new Matrix4f();

        matrix.rotate(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrix.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180F));

        return matrix;
    }

    private boolean shouldStripIrisViewBobbing()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        return BBSRendering.isIrisShadersEnabled()
            && client != null
            && client.options != null
            && Boolean.TRUE.equals(client.options.getBobView().getValue());
    }

    private Matrix4f createViewBobbingMatrix(float tickDelta)
    {
        MatrixStack stack = new MatrixStack();
        MinecraftClient client = MinecraftClient.getInstance();

        try
        {
            ((GameRendererAccessor) client.gameRenderer).bbspp$invokeBobView(stack, tickDelta);
        }
        catch (Throwable ignored)
        {
            return new Matrix4f();
        }

        return new Matrix4f(stack.peek().getPositionMatrix());
    }

    /**
     * 生成单个确定性粒子：用种子随机采样发射参数，需要碰撞或中心停止时重放运动历史，否则用闭式解。
     */
    private SprayedItem createDeterministicItem(List<ItemStack> items, World collisionWorld, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, Vector3d origin, Matrix3f rotation, int spawnTick, int index, float age, int maxAge)
    {
        ItemStack stack = items.get(this.emitter.randomIndex(spawnTick, index, 0, items.size())).copy();
        Random seeded = new Random(this.emitter.mixSeed(spawnTick, index));

        float speed = this.form.speed.get();
        ItemSprayEmitter.EmissionSample sample = this.emitter.createEmissionSample(seeded);

        float currentSpeed = speed;
        float speedOffset = this.form.speedOffset.get();
        if (speedOffset > 0)
        {
            currentSpeed += (seeded.nextFloat() * 2 - 1) * speedOffset;

            if (speed >= 0 && currentSpeed < 0)
            {
                currentSpeed = 0;
            }
            else if (speed < 0 && currentSpeed > 0)
            {
                currentSpeed = 0;
            }
        }

        Vector3f localVelocity = new Vector3f(sample.direction).normalize().mul(currentSpeed);
        rotation.transform(localVelocity);

        Vector3d pos = new Vector3d(origin);
        Vector3d centerStopTarget = this.emitter.shouldStopAtCenter(currentSpeed) ? new Vector3d(origin) : null;
        Vector3f localOffset = new Vector3f(sample.offset);

        rotation.transform(localOffset);
        pos.add(localOffset.x(), localOffset.y(), localOffset.z());

        float scatter = this.form.scatter.get();
        if (scatter > 0)
        {
            Vector3f localScatter = new Vector3f(
                (seeded.nextFloat() * 2 - 1) * scatter,
                (seeded.nextFloat() * 2 - 1) * scatter,
                (seeded.nextFloat() * 2 - 1) * scatter
            );
            rotation.transform(localScatter);
            pos.add(localScatter.x(), localScatter.y(), localScatter.z());

            if (centerStopTarget != null)
            {
                centerStopTarget.add(localScatter.x(), localScatter.y(), localScatter.z());
            }
        }

        Vector3d velocity = new Vector3d(localVelocity.x(), localVelocity.y(), localVelocity.z());

        Vector3f initialRotation = this.emitter.getInitialRotation();
        Vector3f rotationSpeed = this.emitter.createRotationSpeed(seeded);
        float itemScale = this.emitter.createItemScale(seeded);
        int scaleInTime = Math.max(0, this.form.scaleInTime.get());
        boolean useCollision = collisionWorld != null;

        SprayedItem item = new SprayedItem(this.owner, stack, pos, velocity, initialRotation, rotationSpeed, maxAge, this.form.gravity.get(), this.form.gravitySpeed.get(), useCollision, this.form.billboard.get(), itemScale, scaleInTime, this.form.color.get(), null);

        ItemSprayPhysics.configureCenterStop(item, centerStopTarget);

        if (useCollision || item.stopAtCenter)
        {
            ItemSprayPhysics.replayDeterministicMotion(item, collisionWorld, previewTransform, motionWorldOffset, gravityLocal, age);
        }
        else
        {
            ItemSprayPhysics.applyDeterministicMotion(item, gravityLocal, age);
        }

        item.age = Math.max(0, (int) Math.floor(age));
        item.renderAge = Math.max(0F, age);
        item.prevPos.set(item.pos);
        item.prevRotation.set(item.rotation);

        return item;
    }

    private List<ItemStack> getItemStacks()
    {
        List<ItemStack> items = new ArrayList<>();
        for (mchorse.bbs_mod.settings.values.mc.ValueItemStack vis : this.form.items.getList())
        {
            if (vis.get() != null && !vis.get().isEmpty()) items.add(vis.get());
        }

        return items;
    }

    private int getMaxAge(float speed, float range)
    {
        int maxAge = this.form.lifetime.get();
        if (maxAge <= 0)
        {
            float speedAbs = Math.abs(speed);
            maxAge = speedAbs > 0.0001F ? Math.max(1, (int) (range / speedAbs)) : 20;
        }

        return maxAge;
    }
}
