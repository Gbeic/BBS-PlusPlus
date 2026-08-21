package gbeic.bbsplusplus.client.structure;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.structure.StructureStickRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * BBS VFX 破坏魔杖的选区增强。
 * <p>
 * VFX 原版魔杖只在方块交互回调里用潜行 / 非潜行区分两个点，既没有拖拽预览也没有
 * 清晰的端点反馈。这里沿用结构棒的交互思路，在 BBS++ 客户端侧接管
 * {@code xavin:destruction_wand}：长按使用键拖拽选区，短按使用键移动起点，短按攻击键
 * 移动终点，长按攻击键清除选区，并把最终选区同步回 VFX 的
 * {@code org.xavin.xavin.DestructionSelection} 静态字段，供原插件的“捕获破坏盒”按钮读取。
 * </p>
 */
public final class VFXDestructionWandSelection
{
    private static final Identifier DESTRUCTION_WAND = new Identifier("xavin", "destruction_wand");

    /** 按住使用键超过这么多 tick 才算拖拽，否则视为单击 */
    private static final int HOLD_THRESHOLD_TICKS = 8;

    /** 拖拽音效的最小间隔 */
    private static final int DRAG_SOUND_COOLDOWN_TICKS = 1;

    /** 长按攻击键达到这么多 tick 清除选区 */
    private static final int ATTACK_CLEAR_TICKS = 40;

    /** 准星没有指向方块时，取视线前方这么远的位置 */
    private static final double FALLBACK_TARGET_DISTANCE = 4.0D;

    /** 射线检测距离 */
    private static final double RAYCAST_DISTANCE = 5.0D;

    private static final float CUSTOM_SOUND_VOLUME = 1.5F;
    private static final float LINE_WIDTH = 3.0F;

    private static BlockPos start;
    private static BlockPos end;

    private static BlockPos dragStart;
    private static BlockPos dragEnd;

    private static boolean using;
    private static int useTicks;
    private static boolean longAction;

    private static int attackTicks;
    private static boolean attackLongHandled;
    private static boolean attackSessionActive;

    private static long lastDragMeasure;
    private static int soundCooldown;
    private static float dragPitch = 1.0F;

    private static Field vfxPos1;
    private static Field vfxPos2;
    private static boolean reflectionResolved;
    private static boolean reflectionAvailable;

    private VFXDestructionWandSelection()
    {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(VFXDestructionWandSelection::tick);
        WorldRenderEvents.LAST.register(VFXDestructionWandSelection::render);
    }

    /** 手持破坏魔杖且没打开界面时，屏蔽 VFX 原本的方块交互回调。 */
    public static boolean shouldBlockUse(MinecraftClient client)
    {
        return isHoldingWand(client) && client.currentScreen == null;
    }

    /** 手持破坏魔杖且没打开界面时，屏蔽原版挖掘与攻击。 */
    public static boolean shouldBlockAttack(MinecraftClient client)
    {
        return isHoldingWand(client) && client.currentScreen == null;
    }

    /** 只有手持破坏魔杖时才显示破坏盒预览边框。 */
    public static boolean shouldRenderSelection(MinecraftClient client)
    {
        return isHoldingWand(client);
    }

    public static boolean isDestructionWand(ItemStack stack)
    {
        return stack != null && DESTRUCTION_WAND.equals(Registries.ITEM.getId(stack.getItem()));
    }

    private static void tick(MinecraftClient client)
    {
        if (!isHoldingWand(client))
        {
            resetInputState();

            return;
        }

        if (client.currentScreen != null)
        {
            resetActiveInputState();
            syncSelection();

            return;
        }

        if (soundCooldown > 0)
        {
            soundCooldown--;
        }

        boolean usePressed = client.options.useKey.isPressed();
        boolean attackPressed = isAttackPressed(client);

        if (usePressed && !using)
        {
            beginUse(client);
        }
        else if (usePressed)
        {
            continueUse(client);
        }
        else if (using)
        {
            finishUse(client);
        }

        if (attackPressed)
        {
            handleAttackHold(client);
        }
        else if (attackSessionActive)
        {
            finishAttack(client);
        }

        syncSelection();
    }

    private static void beginUse(MinecraftClient client)
    {
        using = true;
        useTicks = 0;
        longAction = false;
        dragStart = targetBlock(client);
        dragEnd = dragStart;
        lastDragMeasure = getDragMeasure(dragStart, dragEnd);
        soundCooldown = 0;
        dragPitch = 1.0F;

        playSound(client, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
    }

    private static void continueUse(MinecraftClient client)
    {
        dragEnd = targetBlock(client);

        updateDragSound(client);

        if (++useTicks >= HOLD_THRESHOLD_TICKS)
        {
            longAction = true;
        }
    }

    private static void finishUse(MinecraftClient client)
    {
        boolean completedDrag = false;

        if (longAction)
        {
            if (dragStart != null && dragEnd != null)
            {
                start = dragStart;
                end = dragEnd;
                completedDrag = true;

                notify(client, Text.translatable("bbsplusplus.vfx_wand.selected"));
            }
        }
        else if (start != null && end != null)
        {
            start = targetBlock(client);

            notify(client, Text.translatable("bbsplusplus.vfx_wand.start_moved"));
        }
        else
        {
            start = targetBlock(client);
            end = start;

            notify(client, Text.translatable("bbsplusplus.vfx_wand.selected"));
        }

        if (completedDrag)
        {
            playSound(client, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.5F);
        }

        using = false;
        useTicks = 0;
        longAction = false;
        dragStart = null;
        dragEnd = null;
        lastDragMeasure = 0L;
    }

    /**
     * 拖拽时按选区体积变化播放提示音，越拖越大音高上升，反之下降。
     */
    private static void updateDragSound(MinecraftClient client)
    {
        if (dragStart == null || dragEnd == null || soundCooldown > 0)
        {
            return;
        }

        long measure = getDragMeasure(dragStart, dragEnd);

        if (measure == lastDragMeasure)
        {
            return;
        }

        dragPitch = measure > lastDragMeasure
            ? Math.min(2.0F, dragPitch + 0.1F)
            : Math.max(0.5F, dragPitch - 0.1F);
        lastDragMeasure = measure;
        soundCooldown = DRAG_SOUND_COOLDOWN_TICKS;

        playSound(client, StructureStickRegistry.STRUCTURE_STICK_DRAG, CUSTOM_SOUND_VOLUME, dragPitch);
    }

    private static void handleAttackHold(MinecraftClient client)
    {
        if (using)
        {
            return;
        }

        if (!attackSessionActive)
        {
            attackSessionActive = true;
            attackTicks = 0;
            attackLongHandled = false;
        }

        if (attackLongHandled)
        {
            return;
        }

        if (start != null && end != null && ++attackTicks >= ATTACK_CLEAR_TICKS)
        {
            clearSelection(client);

            attackLongHandled = true;
        }
    }

    private static void finishAttack(MinecraftClient client)
    {
        if (!attackLongHandled && attackTicks >= ATTACK_CLEAR_TICKS && start != null && end != null)
        {
            clearSelection(client);

            attackLongHandled = true;
        }

        if (!attackLongHandled)
        {
            moveEnd(client);
        }

        attackTicks = 0;
        attackLongHandled = false;
        attackSessionActive = false;
    }

    private static void moveEnd(MinecraftClient client)
    {
        if (start == null)
        {
            start = targetBlock(client);
        }

        end = targetBlock(client);

        notify(client, Text.translatable("bbsplusplus.vfx_wand.end_moved"));
    }

    private static void clearSelection(MinecraftClient client)
    {
        start = null;
        end = null;
        dragStart = null;
        dragEnd = null;

        syncSelection();
        notify(client, Text.translatable("bbsplusplus.vfx_wand.cleared"));
    }

    private static void syncSelection()
    {
        if (!resolveReflection())
        {
            return;
        }

        try
        {
            vfxPos1.set(null, start);
            vfxPos2.set(null, end);
        }
        catch (Exception e)
        {
            reflectionAvailable = false;
        }
    }

    private static boolean resolveReflection()
    {
        if (reflectionResolved)
        {
            return reflectionAvailable;
        }

        reflectionResolved = true;

        try
        {
            Class<?> selection = Class.forName("org.xavin.xavin.DestructionSelection");

            vfxPos1 = selection.getField("pos1");
            vfxPos2 = selection.getField("pos2");
            reflectionAvailable = true;
        }
        catch (Exception e)
        {
            reflectionAvailable = false;
        }

        return reflectionAvailable;
    }

    /** 取准星指向的方块，没打到方块时取视线前方固定距离的位置。 */
    private static BlockPos targetBlock(MinecraftClient client)
    {
        if (client.player == null || client.world == null)
        {
            return BlockPos.ORIGIN;
        }

        Vec3d camera = client.player.getCameraPosVec(1.0F);
        Vec3d direction = client.player.getRotationVec(1.0F);
        Vec3d reach = camera.add(direction.multiply(RAYCAST_DISTANCE));
        BlockHitResult hit = client.world.raycast(new RaycastContext(
            camera, reach, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK)
        {
            return hit.getBlockPos();
        }

        return BlockPos.ofFloored(camera.add(direction.multiply(FALLBACK_TARGET_DISTANCE)));
    }

    /**
     * 绘制选区线框：整体框、起点（绿）、终点（红），以及准星当前指向的方块（蓝）。
     */
    private static void render(WorldRenderContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!shouldRenderSelection(client))
        {
            return;
        }

        BlockPos a = using ? dragStart : start;
        BlockPos b = using ? dragEnd : end;
        BlockPos preview = using ? null : targetBlock(client);

        if ((a == null || b == null) && preview == null)
        {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        if (a != null && b != null)
        {
            drawBox(builder, matrices, bounds(a, b), 1.0F, 0.65F, using ? 0.0F : 0.2F, 1.0F);
            drawBox(builder, matrices, new Box(a), 0.0F, 1.0F, 0.0F, 1.0F);
            drawBox(builder, matrices, new Box(b), 1.0F, 0.0F, 0.0F, 1.0F);
        }

        if (preview != null)
        {
            drawBox(builder, matrices, new Box(preview), 0.1F, 0.45F, 1.0F, 1.0F);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();

        matrices.pop();
    }

    private static Box bounds(BlockPos a, BlockPos b)
    {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 选区体积，用来判断拖拽是变大还是变小。 */
    private static long getDragMeasure(BlockPos a, BlockPos b)
    {
        if (a == null || b == null)
        {
            return 0L;
        }

        long x = Math.abs(a.getX() - b.getX()) + 1L;
        long y = Math.abs(a.getY() - b.getY()) + 1L;
        long z = Math.abs(a.getZ() - b.getZ()) + 1L;

        return x * y * z;
    }

    private static void drawBox(BufferBuilder builder, MatrixStack matrices, Box box, float r, float g, float b, float a)
    {
        MatrixStack.Entry entry = matrices.peek();
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        line(builder, entry, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(builder, entry, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(builder, entry, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(builder, entry, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(builder, entry, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(builder, entry, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(builder, entry, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(builder, entry, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(builder, entry, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(builder, entry, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(builder, entry, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(builder, entry, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(BufferBuilder builder, MatrixStack.Entry entry, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a)
    {
        builder.vertex(entry.getPositionMatrix(), (float) x1, (float) y1, (float) z1).color(r, g, b, a).next();
        builder.vertex(entry.getPositionMatrix(), (float) x2, (float) y2, (float) z2).color(r, g, b, a).next();
    }

    private static void playSound(MinecraftClient client, SoundEvent event, float volume, float pitch)
    {
        client.getSoundManager().play(PositionedSoundInstance.master(event, pitch, volume));
    }

    private static boolean isHoldingWand(MinecraftClient client)
    {
        return client != null
            && client.player != null
            && isDestructionWand(client.player.getMainHandStack());
    }

    /**
     * 直接查询攻击键的物理按下状态，避免和原版按键消费逻辑互相影响。
     */
    private static boolean isAttackPressed(MinecraftClient client)
    {
        if (client == null || client.getWindow() == null)
        {
            return false;
        }

        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(client.options.attackKey);
        long handle = client.getWindow().getHandle();

        if (key.getCategory() == InputUtil.Type.MOUSE)
        {
            return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        }

        if (key.getCategory() == InputUtil.Type.KEYSYM)
        {
            return InputUtil.isKeyPressed(handle, key.getCode());
        }

        if (key.getCategory() == InputUtil.Type.SCANCODE)
        {
            return GLFW.glfwGetKeyScancode(key.getCode()) != -1 && InputUtil.isKeyPressed(handle, key.getCode());
        }

        return client.options.attackKey.isPressed();
    }

    private static void resetInputState()
    {
        resetActiveInputState();
    }

    private static void resetActiveInputState()
    {
        using = false;
        useTicks = 0;
        longAction = false;
        dragStart = null;
        dragEnd = null;
        attackTicks = 0;
        attackLongHandled = false;
        attackSessionActive = false;
        lastDragMeasure = 0L;
        soundCooldown = 0;
        dragPitch = 1.0F;
    }

    private static void notify(MinecraftClient client, Text message)
    {
        if (client.player != null)
        {
            client.player.sendMessage(message, true);
        }
    }
}
