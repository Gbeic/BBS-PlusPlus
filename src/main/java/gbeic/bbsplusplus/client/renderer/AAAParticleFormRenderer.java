package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.pose.Transform;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.api.client.EffectDefinition;
import mod.chloeprime.aaaparticles.client.render.EffekRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AAA 粒子表单渲染器。
 * <p>
 * 使用 AAA Particles Mod 渲染 Effekseer 粒子效果。
 * 支持骨骼绑定、循环播放、寻帧记忆、速度控制等功能。
 * </p>
 */
public class AAAParticleFormRenderer extends FormRenderer<AAAParticleForm> implements ITickable
{
    /* 全局活跃渲染器注册表，用于确保资源清理 */
    public static final List<AAAParticleFormRenderer> activeRenderers = new CopyOnWriteArrayList<>();
    /** 最近一次 AAA 表单预览绘制时间；编辑器会复制表单，因此必须按渲染域而非对象身份互斥。 */
    private static long lastGlobalPreviewRenderTime;
    /** 记录模型方块实体和真实方块位置的对应关系，用来精确判断发射源是否还存在 */
    private static final Map<IEntity, Source> MODEL_BLOCK_SOURCES = new WeakHashMap<>();
    /** 每个 BBS 实体当前唯一有效的世界渲染器；编辑保存产生新实例时永久淘汰旧实例。 */
    private static final Map<IEntity, AAAParticleFormRenderer> ENTITY_RENDERERS = new WeakHashMap<>();

    static
    {
        ModelBlockEntityUpdateCallback.EVENT.register(modelBlock ->
        {
            Level world = modelBlock.getLevel();
            IEntity entity = modelBlock.getEntity();

            if (world != null && entity != null)
            {
                MODEL_BLOCK_SOURCES.put(entity, new Source(world, modelBlock.getBlockPos(), modelBlock));
            }
        });
    }

    private static class Source
    {
        public final ResourceKey<Level> worldKey;
        public final BlockPos pos;
        /** 只保存模型方块实例身份码，避免 Source 反向强引用实体导致 WeakHashMap 无法释放旧条目 */
        public final int blockEntityIdentity;

        public Source(Level world, BlockPos pos, ModelBlockEntity modelBlock)
        {
            this.worldKey = world.dimension();
            this.pos = pos.immutable();
            this.blockEntityIdentity = System.identityHashCode(modelBlock);
        }

        public boolean isAlive(Level world)
        {
            if (world == null || !world.dimension().equals(this.worldKey))
            {
                return false;
            }

            if (world.getBlockEntity(this.pos) instanceof ModelBlockEntity modelBlock)
            {
                return System.identityHashCode(modelBlock) == this.blockEntityIdentity;
            }

            return false;
        }
    }

    public int smartFreezeLoops = 0;

    /* 固定预览图路径 */
    public static final Link ICON = Link.assets("textures/AAA.png");

    /* 寻帧记忆系统 —— 存储每帧的粒子状态，用于时间线拖拽恢复 */
    private static class ParticleState
    {
        boolean wasPlaying;
        ResourceLocation effectId;

        ParticleState(boolean wasPlaying, ResourceLocation effectId)
        {
            this.wasPlaying = wasPlaying;
            this.effectId = effectId;
        }
    }

    private static java.lang.reflect.Method effekRenderHandMethod = null;
    private static Object effekMainHandEnum = null;
    private static boolean effekRenderHandMethodResolved = false;

    /* 优化缓存字段 */
    private Link lastEffectLink = null;
    private ResourceLocation cachedEffectId = null;
    private final Matrix4f tempMatrix = new Matrix4f();
    private final Vector3f tempVec = new Vector3f();
    private final float[] tempMat = new float[12];

    /* 发射器状态 */
    private ParticleEmitter emitter;
    private ResourceLocation lastEffectId;

    public ResourceLocation getLastEffectId()
    {
        return this.lastEffectId;
    }

    public void forceStop()
    {
        this.stopEmitter();
    }
    private boolean lastPaused = false;
    private boolean lastRestart = false;
    /* 待发送的触发器队列 */
    private final boolean[] pendingTriggers = new boolean[4];
    /* 上次检查关键帧触发器的 tick，用于防止同一 tick 内重复触发 */
    private int lastTriggerCheckTick = Integer.MIN_VALUE;
    /* 上次触发器的值，用于上升沿检测（只在 false -> true 时触发） */
    private final boolean[] lastTriggerValues = new boolean[4];

    /* 影片同步 */
    private int currentTick = -1;
    private int lastTick = -1;
    private int lastRenderTick = -1;
    private boolean filmWasPlaying = false;
    private boolean hadForm = false;
    private Map<Integer, ParticleState> tickMemory = new LinkedHashMap<Integer, ParticleState>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ParticleState> eldest) {
            return size() > 1000;
        }
    };
    private long lastTickTime = 0;
    private long lastRenderTime = 0;
    private FormRenderType lastRenderType = null;
    private float editorProgress = 0f;
    private long lastEditorTime = 0;
    /* 缓存当前特效的最大帧数，用于循环倒带 */
    private int effectMaxTerm = 0;
    /** 非循环特效结束时的编辑参数快照，用于在用户改参数后自动恢复预览。 */
    private int endedSettingsHash = Integer.MIN_VALUE;

    /* 发射器唯一标识符 */
    private final ResourceLocation emitterName;

    /* 底层 Minecraft 实体弱引用，用于瞬时清理判断 */
    private WeakReference<Object> lastMcEntityRef = null;
    /** 已被同一实体的新渲染器替代，禁止底层后续回调再次创建发射器。 */
    private boolean superseded;

    /* 记录最后一次渲染时所在的 UI 界面类名 */
    private String lastScreenClass = null;

    /* 记录最后一次渲染是否使用了虚拟实体（如影片回放） */
    private boolean wasStubEntity = false;

    /* 模型方块渲染源，用于判断对应方块是否被破坏 */
    private Source modelBlockSource = null;

    public AAAParticleFormRenderer(AAAParticleForm form)
    {
        super(form);

        this.emitterName = ResourceLocation.fromNamespaceAndPath(BBSMod.MOD_ID, "form_" + UUID.randomUUID().toString().replace("-", ""));
        activeRenderers.add(this);
    }

    private int getLoopStart() {
        // 关键帧输出再大，也强行压制在 500 以内
        return Math.min(500, Math.max(0, this.form.loopStart.get()));
    }

    private int getLoopEnd() {
        // 结束帧强压在 500 以内，且至少不低于起始帧
        return Math.min(500, Math.max(this.getLoopStart(), this.form.loopEnd.get()));
    }

    /**
     * 把影片时间轴 tick 换算为 Effekseer 的默认 60 FPS 帧。
     * Minecraft 影片以每秒 20 tick 推进，因此一个影片 tick 对应三个特效帧。
     */
    private float getTimelineProgress(int tick)
    {
        float speed = Math.max(0.01f, Math.min(10.0f, this.form.speed.get()));
        float progress = Math.max(0, tick) * 3F * speed;

        if (this.form.loop.get())
        {
            int start = this.getLoopStart();
            int end = this.getLoopEnd();
            int minTerm = start > 0 || end > 0 ? start : 0;
            int maxTerm = end > 0 ? end : this.effectMaxTerm;

            if (maxTerm > minTerm && progress >= maxTerm)
            {
                progress = minTerm + ((progress - minTerm) % (maxTerm - minTerm));
            }
        }
        else
        {
            // 非循环寻帧同样遵守 500 帧性能保护；已知特效长度时不推进到结尾之外
            int safeEnd = this.effectMaxTerm > 0 ? Math.min(500, this.effectMaxTerm) : 500;
            progress = Math.min(progress, safeEnd);
        }

        return progress;
    }

    /**
     * 时间轴发生跳转后，将新发射器推进到目标影片时刻。
     * 这样从中间开始播放和从头播放到同一时刻会使用一致的时间基准。
     */
    private void seekEmitterToTimeline(int tick)
    {
        if (this.emitter == null || !this.emitter.exists())
        {
            return;
        }

        this.editorProgress = this.getTimelineProgress(tick);
        this.emitter.setProgress(this.editorProgress);
        this.lastEditorTime = System.currentTimeMillis();

        // 跳转只恢复目标画面，不把目标帧上已经为 true 的触发器误判成一次新上升沿
        this.lastTriggerValues[0] = this.form.trigger0.get();
        this.lastTriggerValues[1] = this.form.trigger1.get();
        this.lastTriggerValues[2] = this.form.trigger2.get();
        this.lastTriggerValues[3] = this.form.trigger3.get();
        this.lastTriggerCheckTick = tick;
        java.util.Arrays.fill(this.pendingTriggers, false);
    }

    /**
     * 计算会影响非循环粒子预览的参数摘要。
     * 只在发射器已经结束时比较，避免正常关键帧播放过程中反复重建。
     */
    private int getPreviewSettingsHash()
    {
        Transform transform = this.form.transform.get();

        return java.util.Objects.hash(
            this.form.speed.get(), this.form.particleScale.get(),
            this.form.dynamicInput0.get(), this.form.dynamicInput1.get(),
            this.form.dynamicInput2.get(), this.form.dynamicInput3.get(),
            transform.translate.x, transform.translate.y, transform.translate.z,
            transform.rotate.x, transform.rotate.y, transform.rotate.z,
            transform.scale.x, transform.scale.y, transform.scale.z
        );
    }

    /**
     * 被全局客户端 tick 调用，清理被遗弃的特效
     */
    public boolean checkCleanup()
    {
        long elapsed = System.currentTimeMillis() - this.lastRenderTime;

        if (this.superseded)
        {
            this.stopEmitter();
            return true;
        }

        // 每个客户端 tick 末尾先隐藏预览发射器；下一次预览绘制会按表单可见性重新打开
        if (this.lastRenderType == FormRenderType.PREVIEW && this.emitter != null)
        {
            this.emitter.setVisibility(false);
        }

        // 预览一旦停止绘制就在下一次客户端 tick 直接停止，禁止第一人称预览粒子残留到世界
        if (this.lastRenderType == FormRenderType.PREVIEW && elapsed > 33)
        {
            this.stopEmitter();
            return true;
        }

        // 1. 对于模型方块 (MODEL_BLOCK) 绑定的粒子，检查对应源方块是否仍然存在
        if (this.lastRenderType == FormRenderType.MODEL_BLOCK)
        {
            Level world = Minecraft.getInstance().level;

            if (this.modelBlockSource != null && !this.modelBlockSource.isAlive(world))
            {
                this.stopEmitter();
                return true;
            }
        }

        // 2. 尝试判断底层实体（影片编辑器中的 ActorEntity 等）是否已经被彻底移除
        if (this.lastMcEntityRef != null)
        {
            Object mcObj = this.lastMcEntityRef.get();
            if (mcObj != null)
            {
                try {
                    java.lang.reflect.Method m = mcObj.getClass().getMethod("isRemoved");
                    boolean removed = (Boolean) m.invoke(mcObj);
                    if (removed) {
                        this.stopEmitter();
                        return true;
                    }
                } catch (Exception e) {}
            }
        }

        // 3. 影片快捷键退出检测 (普通界面下)
        // BBS 可以使用快捷键在无 UI 状态下播放影片，此时用的是虚拟实体
        if (this.lastRenderType == FormRenderType.ENTITY && this.wasStubEntity && elapsed > 33)
        {
            net.minecraft.client.gui.screens.Screen currentScreen = net.minecraft.client.Minecraft.getInstance().screen;
            // 只有在普通游玩界面（无 UI）时才检查快捷键导致的影片停止
            if (currentScreen == null) {
                boolean inAnyFilm = false;
                try {
                    Class<?> clientClass = Class.forName("mchorse.bbs_mod.BBSModClient");
                    java.lang.reflect.Method getFilms = clientClass.getMethod("getFilms");
                    Object filmsObj = getFilms.invoke(null);
                    
                    java.lang.reflect.Field controllersField = filmsObj.getClass().getDeclaredField("controllers");
                    controllersField.setAccessible(true);
                    java.util.List<?> controllers = (java.util.List<?>) controllersField.get(filmsObj);
                    
                    if (controllers != null && !controllers.isEmpty()) {
                        inAnyFilm = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    inAnyFilm = true; // 异常兜底
                }
                
                // 如果没有任何正在播放的影片，立刻销毁
                if (!inAnyFilm) {
                    this.stopEmitter();
                    return true;
                }
            }
        }

        // 4. 影片编辑器/UI 界面退出检测
        // 如果粒子是在某个特定的 UI（如 UIDashboard 影片编辑器）中渲染的，且现在该 UI 已经关闭或切换
        if (this.lastScreenClass != null && elapsed > 33)
        {
            net.minecraft.client.gui.screens.Screen currentScreen = net.minecraft.client.Minecraft.getInstance().screen;
            String currentScreenClass = currentScreen != null ? currentScreen.getClass().getName() : null;
            
            if (!this.lastScreenClass.equals(currentScreenClass))
            {
                this.stopEmitter();
                return true;
            }
        }

        /*
         * 不再按“超过一秒未渲染”销毁发射器。未渲染通常只是视锥剔除、掉帧或切出窗口，
         * 并不代表表单已经消失；按时间清理会让玩家转回视角时看到粒子从头播放。
         */
        return false;
    }

    /** 判断当前世界发射器是否应当为正在绘制的 AAA 表单预览让路。 */
    private boolean isSuppressedByPreview()
    {
        return this.lastRenderType != FormRenderType.PREVIEW
            && System.currentTimeMillis() - lastGlobalPreviewRenderTime <= 100;
    }

    public ParticleEmitter getEmitter()
    {
        return this.emitter;
    }

    /**
     * 从表单的特效链接中获取 ResourceLocation
     */
    private ResourceLocation getEffectId()
    {
        Link effect = this.form.effect.get();

        if (effect == null)
        {
            return null;
        }

        if (effect == this.lastEffectLink)
        {
            return this.cachedEffectId;
        }

        this.lastEffectLink = effect;

        String path = effect.path;

        if (path == null || path.isEmpty())
        {
            return null;
        }

        // 去除 .efkefc 扩展名
        if (path.endsWith(".efkefc"))
        {
            path = path.substring(0, path.length() - 7);
        }

        // 去除 effeks/ 前缀
        if (path.startsWith("effeks/"))
        {
            path = path.substring(7);
        }

        try
        {
            this.cachedEffectId = ResourceLocation.fromNamespaceAndPath(effect.source, path);
            return this.cachedEffectId;
        }
        catch (RuntimeException e)
        {
            this.cachedEffectId = null;
            return null;
        }
    }

    /**
     * 确保发射器已创建且状态最新
     */
    private void ensureEmitter()
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        if (!BBSEffectLoader.canLoadExternalEffects())
        {
            return;
        }

        ResourceLocation effectId = this.getEffectId();

        if (effectId == null)
        {
            this.stopEmitter();
            return;
        }

        // 检查特效是否改变
        if (this.lastEffectId == null || !this.lastEffectId.equals(effectId))
        {
            ResourceLocation oldId = this.lastEffectId;
            this.stopEmitter();
            this.lastEffectId = effectId;
            this.tickMemory.clear();
            this.effectMaxTerm = 0;

            // 卸载旧特效释放 native 资源（异步，避免阻塞渲染）
            if (oldId != null)
            {
                BBSEffectLoader.unloadEffect(oldId);
            }
        }

        // 通过 BBSEffectLoader 获取 EffectDefinition
        EffectDefinition definition = BBSEffectLoader.getOrLoad(effectId);

        if (definition == null)
        {
            return;
        }

        // 缓存特效最大帧数，用于循环模式的主动倒带
        if (this.effectMaxTerm <= 0)
        {
            try
            {
                this.effectMaxTerm = definition.getEffect().maxTerm();
            }
            catch (Exception e)
            {
                this.effectMaxTerm = 0;
            }
        }

        // 根据渲染上下文选择发射器类型
        boolean isPreview = this.lastRenderType == FormRenderType.PREVIEW;
        ParticleEmitter.Type targetType = isPreview ? ParticleEmitter.Type.FIRST_PERSON_MAINHAND : ParticleEmitter.Type.WORLD;

        // 如果类型变化，重建发射器
        if (this.emitter != null && this.emitter.type != targetType)
        {
            this.stopEmitter();
        }

        // 创建发射器
        if (this.emitter == null)
        {
            this.emitter = definition.play(targetType, this.emitterName);

            if (this.emitter != null)
            {
                if (!activeRenderers.contains(this))
                {
                    activeRenderers.add(this);
                }
                
                // 如果是循环模式且设置了合法的起始帧，首次创建时直接从起始帧开始播放
                int start = this.getLoopStart();
                int end = this.getLoopEnd();
                boolean customLoop = start > 0 || end > 0;
                if (this.form.loop.get() && customLoop)
                {
                    this.editorProgress = start;
                    this.emitter.setProgress(this.editorProgress);
                }
            }
        }

        if (this.emitter == null)
        {
            return;
        }

        // 检测影片是否正在播放
        boolean filmPlaying;
        if (this.lastRenderType == FormRenderType.ENTITY)
        {
            long currentTime = System.currentTimeMillis();
            filmPlaying = (currentTime - this.lastTickTime) < 100;
        }
        else
        {
            filmPlaying = true;
        }

        // 是否定格
        boolean freeze = this.form.loop.get() && this.getLoopStart() == this.getLoopEnd() && this.getLoopStart() > 0;
        boolean nativePause = !this.form.forceFreeze.get();
        boolean isSmartPaused = false;

        if (freeze && !nativePause)
        {
            float target = this.getLoopStart();
            // 如果粒子离开视野导致渲染停滞超过1000ms，并且它处于已经定格的状态，则重置它，让它重生成历史
            if (this.smartFreezeLoops >= 1 && System.currentTimeMillis() - this.lastRenderTime > 1000)
            {
                this.smartFreezeLoops = 0;
                this.editorProgress = Math.max(0, target - 1);
                this.emitter.setProgress(this.editorProgress);
            }

            // 如果进度偏离（超过目标，或落后目标1帧以上），重置进度到 target - 1，让它自然播放
            if (this.editorProgress > target + 0.1f || this.editorProgress < target - 1.1f)
            {
                this.editorProgress = Math.max(0, target - 1);
                this.emitter.setProgress(this.editorProgress);
                this.smartFreezeLoops = 0;
            }
            
            // 只有当它至少经历了 1 次完整的循环重置，并且当前进度到达目标帧时，才触发瞬间暂停
            if (this.smartFreezeLoops >= 1 && this.editorProgress >= target)
            {
                isSmartPaused = true;
            }
        }

        // 更新暂停状态
        boolean shouldPause = this.form.paused.get() || !filmPlaying || (freeze && nativePause) || isSmartPaused;

        if (shouldPause != this.lastPaused)
        {
            if (shouldPause)
            {
                this.emitter.pause();
            }
            else
            {
                this.emitter.resume();
            }
            this.lastPaused = shouldPause;
        }

        // 仅在原生暂停模式下，确保进度强行停在指定帧且同步一次
        if (freeze && nativePause && Math.abs(this.editorProgress - this.getLoopStart()) > 0.01f)
        {
            this.editorProgress = this.getLoopStart();
            this.emitter.setProgress(this.editorProgress);
        }

        // 更新可见性
        this.emitter.setVisibility(this.form.visible.get() && !this.isSuppressedByPreview());

        // 更新 X-Ray 专属管理器迁移（仅在真实世界渲染时生效，UI 预览模式保持原生，避免坐标漂移）
        boolean shouldXRay = this.form.ignoreDepth.get() && targetType == mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter.Type.WORLD;
        gbeic.bbsplusplus.utils.XRayManager.migrate(this.emitter, shouldXRay, definition, targetType);

        // 应用动态输入参数 0-3
        this.emitter.setDynamicInput(0, this.form.dynamicInput0.get());
        this.emitter.setDynamicInput(1, this.form.dynamicInput1.get());
        this.emitter.setDynamicInput(2, this.form.dynamicInput2.get());
        this.emitter.setDynamicInput(3, this.form.dynamicInput3.get());
    }

    /**
     * 停止并清理发射器
     */
    private void stopEmitter()
    {
        if (this.emitter != null)
        {
            this.emitter.setVisibility(false);
            this.emitter.stop();
            this.emitter = null;
        }

        this.lastPaused = false;
        this.lastRestart = false;
        resetTriggers();
        this.tickMemory.clear();
        this.currentTick = -1;
        this.lastTick = -1;
        this.editorProgress = 0f;
        this.smartFreezeLoops = 0;
        this.endedSettingsHash = Integer.MIN_VALUE;

        activeRenderers.remove(this);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        // 绘制固定预览图
        var texture = context.render.getTextures().getTexture(ICON);

        if (texture != null)
        {
            float min = Math.min(texture.width, texture.height);

            if (min <= 0)
            {
                min = 1;
            }

            int ow = (x2 - x1) - 4;
            int oh = (y2 - y1) - 4;

            int w = (int) ((texture.width / min) * ow);
            int h = (int) ((texture.height / min) * ow);

            int x = x1 + (ow - w) / 2 + 2;
            int y = y1 + (oh - h) / 2 + 2;

            context.batcher.fullTexturedBox(texture, x, y, w, h);
        }

        // 在底部显示粒子名称方便区分
        String name = this.form.getDefaultDisplayName();
        context.batcher.text(name, x1 + 4, y2 - 12, 0xffffffff);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        if (this.superseded)
        {
            return;
        }

        this.lastRenderTime = System.currentTimeMillis();
        this.lastRenderType = context.type;

        // 检查实体/表单是否被移除
        boolean isPreview = context.type == FormRenderType.PREVIEW;

        if (!isPreview && context.entity != null)
        {
            AAAParticleFormRenderer previous = ENTITY_RENDERERS.put(context.entity, this);

            if (previous != null && previous != this)
            {
                previous.superseded = true;
                previous.stopEmitter();
            }
        }

        if (isPreview)
        {
            lastGlobalPreviewRenderTime = this.lastRenderTime;

            // 编辑器使用表单副本，无法稳定反查原对象；预览期间统一隐藏世界 AAA 发射器，杜绝双重渲染
            for (AAAParticleFormRenderer renderer : activeRenderers)
            {
                if (renderer != this && renderer.lastRenderType != FormRenderType.PREVIEW && renderer.emitter != null)
                {
                    renderer.emitter.setVisibility(false);
                }
            }
        }

        if (context.entity == null || (context.entity.getForm() == null && context.type != FormRenderType.MODEL_BLOCK && !isPreview))
        {
            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        // 确保实体有正确的表单引用
        if ((context.type == FormRenderType.MODEL_BLOCK || isPreview) && context.entity != null && context.entity.getForm() == null)
        {
            context.entity.setForm(this.form);
        }

        if (context.type == FormRenderType.MODEL_BLOCK)
        {
            this.modelBlockSource = MODEL_BLOCK_SOURCES.get(context.entity);
        }
        else if (!isPreview)
        {
            this.modelBlockSource = null;
        }

        // 追踪实体类型，用于清理判断
        this.wasStubEntity = false;
        if (context.entity != null)
        {
            if (context.entity.getClass().getName().equals("mchorse.bbs_mod.forms.entities.StubEntity")) {
                this.wasStubEntity = true;
            }

            Object mcObj = context.entity;
            try {
                if (context.entity.getClass().getName().equals("mchorse.bbs_mod.forms.entities.MCEntity")) {
                    java.lang.reflect.Method m = context.entity.getClass().getMethod("getMcEntity");
                    mcObj = m.invoke(context.entity);
                }
            } catch (Exception e) {}
            this.lastMcEntityRef = new WeakReference<>(mcObj);
        }

        // 记录渲染时所在的 UI 界面
        net.minecraft.client.gui.screens.Screen currentScreen = net.minecraft.client.Minecraft.getInstance().screen;
        this.lastScreenClass = currentScreen != null ? currentScreen.getClass().getName() : null;

        // 检查特效是否被清除
        if (this.form.effect.get() == null)
        {
            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        this.hadForm = true;

        // 检测时间线跳转（寻帧）
        int renderTick = context.entity.getAge();

        boolean renderSeeked = false;
        if (this.lastRenderTick >= 0 && renderTick != this.lastRenderTick)
        {
            if (renderTick < this.lastRenderTick || Math.abs(renderTick - this.lastRenderTick) > 1)
            {
                renderSeeked = true;
                boolean renderSeekedBackward = renderTick < this.lastRenderTick;

                // 向前跳转可以直接推进现有句柄；只有倒退才需要重建历史状态
                if (renderSeekedBackward)
                {
                    this.stopEmitter();
                }
            }
        }

        this.lastRenderTick = renderTick;
        this.ensureEmitter();

        if (renderSeeked)
        {
            this.seekEmitterToTimeline(renderTick);
        }

        boolean justRecreated = false;

        // === 主动循环倒带 ===
        // 核心思路：在粒子接近结束前，抢在 Effekseer 原生引擎销毁句柄之前，
        // 主动调用 setProgress(0) 将播放进度回绕到起点。
        // 这样粒子句柄永远不会真正"死亡"，循环就是无缝的。
        if (this.emitter != null && this.emitter.exists() && this.form.loop.get())
        {
            int minTerm = 0;
            int maxTerm = this.effectMaxTerm;

            // 只对玩家明确填写的自定义循环范围主动倒带
            int start = this.getLoopStart();
            int end = this.getLoopEnd();
            boolean customLoop = start > 0 || end > 0;
            if (customLoop)
            {
                minTerm = start;
                maxTerm = (end > 0 && end >= start) ? end : this.effectMaxTerm;
            }

            /*
             * 默认循环不使用 maxTerm() 主动倒带。部分复杂特效的 GetTermMax 只覆盖发射阶段，
             * 子节点、拖尾或残留视觉仍会继续存在；按该值倒带会在约 0.3～0.5 秒错误重开。
             * 默认循环改为等待 emitter.exists() 真正结束后走下方后备重建。
             */
            if (customLoop && maxTerm > 0 && minTerm != maxTerm)
            {
                // 计算当前预估进度（上一帧的进度 + 本帧已流逝的时间增量）
                long now = System.currentTimeMillis();
                float predictedProgress = this.editorProgress;
                if (this.lastEditorTime > 0)
                {
                    long dt = now - this.lastEditorTime;
                    float speed = Math.max(0.01f, Math.min(10.0f, this.form.speed.get()));
                    predictedProgress += (dt / 1000f * 60f) * speed;
                }

                // 到达用户设置的结束帧后再倒带，避免吞掉循环末尾的画面
                if (predictedProgress >= maxTerm)
                {
                    // 计算回绕后的精确进度（保留余数，避免累积误差）
                    float duration = (float) (maxTerm - minTerm);
                    float excess = Math.max(0f, predictedProgress - maxTerm);
                    this.editorProgress = minTerm + ((duration > 0) ? (excess % duration) : 0);
                    this.emitter.setProgress(this.editorProgress);
                    this.lastEditorTime = now;
                    this.resetTriggers();
                }
            }
        }

        if (this.emitter == null || !this.emitter.exists())
        {
            // 循环模式的后备方案：如果主动倒带未能阻止原生引擎销毁句柄（例如严重卡顿），
            // 则作为最后手段重建发射器。
            if (this.form.loop.get())
            {
                this.emitter = null;
                this.ensureEmitter();
                if (this.emitter == null)
                {
                    return;
                }
                
                int start = this.getLoopStart();
                int end = this.getLoopEnd();
                int minTerm = 0;
                boolean customLoop = start > 0 || end > 0;
                if (customLoop) {
                    minTerm = start;
                }
                this.editorProgress = minTerm;
                this.lastEditorTime = 0;
                justRecreated = true;
                this.resetTriggers();
            }
            else
            {
                // 非循环模式：检查是否有新的触发器信号需要处理
                // 如果有，重建发射器以响应触发
                int settingsHash = this.getPreviewSettingsHash();
                boolean settingsChanged = this.endedSettingsHash != Integer.MIN_VALUE
                    && this.endedSettingsHash != settingsHash;

                if (this.hasPendingTriggerSignal() || settingsChanged)
                {
                    this.emitter = null;
                    this.ensureEmitter();

                    if (this.emitter == null || !this.emitter.exists())
                    {
                        return;
                    }
                    this.editorProgress = 0;
                    this.endedSettingsHash = Integer.MIN_VALUE;
                    justRecreated = true;
                    // 有活跃的发射器，继续往下执行 consumePendingTriggers
                }
                else
                {
                    this.endedSettingsHash = settingsHash;
                    return;
                }
            }
        }

        // 处理触发器 0-3：消费待发送的触发信号
        if (this.emitter != null && this.emitter.exists())
        {
            this.endedSettingsHash = Integer.MIN_VALUE;
            this.consumePendingTriggers();
        }

        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        // 获取矩阵栈的顶层矩阵
        Matrix4f pose = context.stack.last().pose();

        // 处理全局/本地坐标变换
        Transform t = this.form.transform.get();
        Vector3f tPos = t.translate;
        Vector3f tRot = t.rotate;
        Vector3f tScale = t.scale;

        if (isPreview)
        {
            // 预览模式：使用本地坐标
            this.emitter.setPosition(tPos.x, tPos.y, tPos.z);
            this.emitter.setRotation(tRot.x, tRot.y, tRot.z);

            float formScale = this.form.particleScale.get();
            this.emitter.setScale(tScale.x * formScale, tScale.y * formScale, tScale.z * formScale);

            // 尝试渲染手持效果
            try
            {
                Matrix4f projection = RenderSystem.getProjectionMatrix();
                net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                Object stackObj = context.stack;

                // 查找 EffekRenderer.onRenderHand 方法反射调用
                if (!effekRenderHandMethodResolved)
                {
                    for (java.lang.reflect.Method m : EffekRenderer.class.getMethods())
                    {
                        if (m.getName().equals("onRenderHand") && m.getParameterCount() == 5)
                        {
                            effekRenderHandMethod = m;
                            break;
                        }
                    }
                    if (effekRenderHandMethod != null)
                    {
                        Class<?> handClass = effekRenderHandMethod.getParameterTypes()[1];
                        if (handClass.isEnum())
                        {
                            for (Object constant : handClass.getEnumConstants())
                            {
                                if (constant.toString().equals("MAIN_HAND"))
                                {
                                    effekMainHandEnum = constant;
                                    break;
                                }
                            }
                        }
                    }
                    effekRenderHandMethodResolved = true;
                }

                if (effekRenderHandMethod != null && effekMainHandEnum != null)
                {
                    effekRenderHandMethod.invoke(null, context.transition, effekMainHandEnum, stackObj, projection, camera);
                }
            }
            catch (Exception e)
            {
                // 预览渲染失败静默处理
            }
        }
        else
        {
            // 世界模式：使用 3×4 矩阵传递变换（避免欧拉角分解的 ±90° Y 轴钳位问题）
            //
            // 1.20.1 时期这里要先乘一次视图逆矩阵，把烘焙进 form 矩阵栈的相机视图变换抵消掉。
            // 1.21.1 起 BBS 的 form 矩阵栈栈基已是单位阵——视图变换移到了
            // RenderSystem.getModelViewMatrix()，由着色器在绘制时再乘回去（见 FSR 的
            // MatrixStackUtils.billboard() 注释）。此时栈里只剩模型自身的变换，再乘视图逆
            // 等于凭空多转一次，粒子会被放到错误的世界位置（表现为世界里看不到，而表单预览
            // 走另一条坐标路径所以正常）。
            //
            // 对齐 FSR 自己的 ParticleFormRenderer.render3D()：CAMERA_RELATIVE_WORLD 直接取
            // context.stack.last().pose()，只有 UI 预览路径才乘 camera.view 的逆。
            // 物品喷射（ItemSprayFormRenderer）此前是同一个病因，已按同样方式修正。
            this.tempMatrix.set(pose);

            // 提取平移 + 摄像机世界坐标
            this.tempMatrix.getTranslation(this.tempVec);
            org.joml.Vector3d camPos = context.camera.position;
            float finalX = (float) (this.tempVec.x + camPos.x);
            float finalY = (float) (this.tempVec.y + camPos.y);
            float finalZ = (float) (this.tempVec.z + camPos.z);

            // 构建 3×4 行主序矩阵
            float ps = this.form.particleScale.get();
            this.tempMat[0] = this.tempMatrix.m00() * ps; this.tempMat[1] = this.tempMatrix.m10() * ps; this.tempMat[2] = this.tempMatrix.m20() * ps; this.tempMat[3] = finalX;
            this.tempMat[4] = this.tempMatrix.m01() * ps; this.tempMat[5] = this.tempMatrix.m11() * ps; this.tempMat[6] = this.tempMatrix.m21() * ps; this.tempMat[7] = finalY;
            this.tempMat[8] = this.tempMatrix.m02() * ps; this.tempMat[9] = this.tempMatrix.m12() * ps; this.tempMat[10] = this.tempMatrix.m22() * ps; this.tempMat[11] = finalZ;
            
            this.emitter.setTransformMatrix(this.tempMat);

        }

        // 处理速度控制
        float speed = Math.max(0.01f, Math.min(10.0f, this.form.speed.get()));
        boolean manualSpeed = Math.abs(speed - 1.0f) > 0.001f;
        boolean isGamePaused = Minecraft.getInstance().isPaused();
        boolean shouldUpdate = !this.lastPaused && (!isGamePaused || isPreview);
        

        if (shouldUpdate)
        {
            long now = System.currentTimeMillis();

            if (this.lastEditorTime == 0)
            {
                this.lastEditorTime = now;
            }

            long timeDiff = now - this.lastEditorTime;
            this.lastEditorTime = now;

            float framesToAdd = (timeDiff / 1000f * 60f) * speed;

            // 限制大跳帧
            if (Math.abs(framesToAdd) > 10f)
            {
                framesToAdd = Math.signum(framesToAdd) * 10f;
            }

            if (framesToAdd < 0f && speed >= 0)
            {
                framesToAdd = 0f;
            }

            this.editorProgress += framesToAdd;

            // 循环定格逻辑（智能定格模式）：先让它播放一轮 (39-40) 充分建立丝带历史，然后再精准暂停
            boolean freeze = this.form.loop.get() && this.getLoopStart() == this.getLoopEnd() && this.getLoopStart() > 0;
            boolean nativePause = !this.form.forceFreeze.get();
            if (freeze && !nativePause)
            {
                float target = this.getLoopStart();
                if (this.editorProgress >= target)
                {
                    if (this.smartFreezeLoops < 1) {
                        // 还没有完成一轮前置循环，将其拨回 target - 1 继续播放
                        this.editorProgress = Math.max(0, target - 1);
                        if (this.emitter != null) {
                            this.emitter.setProgress(this.editorProgress);
                        }
                        this.smartFreezeLoops++;
                    } else {
                        // 已经完整循环过，将其精准停在 target
                        this.editorProgress = target;
                    }
                }
            }
        }
        else
        {
            this.lastEditorTime = 0;
        }

        // 循环模式下的后备回绕，确保进度不超过结束帧
        boolean didFallbackRewind = false;
        if (this.form.loop.get())
        {
            int minTerm = 0;
            int maxTerm = this.effectMaxTerm;
            boolean customLoop = this.getLoopEnd() > this.getLoopStart() || 
                                 (this.getLoopEnd() == this.getLoopStart() && this.getLoopStart() > 0);
            if (customLoop)
            {
                minTerm = this.getLoopStart();
                maxTerm = this.getLoopEnd();
            }

            if (customLoop && maxTerm > 0 && minTerm != maxTerm && this.editorProgress >= maxTerm)
            {
                float duration = (float) (maxTerm - minTerm);
                float excess = Math.max(0f, this.editorProgress - maxTerm);
                this.editorProgress = minTerm + ((duration > 0) ? (excess % duration) : 0);
                didFallbackRewind = true;
            }
        }

        // 手动速度控制、游戏暂停、或者本帧刚刚循环重建（或触发后备回绕），手动设置进度强制刷新一帧
        if (manualSpeed || (isGamePaused && isPreview) || justRecreated || didFallbackRewind)
        {
            this.emitter.setProgress(this.editorProgress);
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        // 检查实体是否被移除
        if (entity == null || entity.getForm() == null)
        {
            if (this.lastRenderType == FormRenderType.PREVIEW)
            {
                this.ensureEmitter();
                this.lastTickTime = System.currentTimeMillis();
                this.hadForm = true;

                // 预览模式下也处理重启逻辑
                boolean restart = this.form.restart.get();
                if (restart && !this.lastRestart && this.emitter != null)
                {
                    this.emitter.stop();
                    this.emitter = null;
                    this.ensureEmitter();
                    resetTriggers();
                }
                this.lastRestart = restart;
                if (restart)
                {
                    this.form.restart.set(false);
                }

                return;
            }

            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        if (this.form.effect.get() == null)
        {
            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        this.hadForm = true;
        this.lastTickTime = System.currentTimeMillis();
        this.currentTick = entity.getAge();
        int targetTick = this.currentTick;

        // 检测时间线跳转
        boolean seeked = this.lastTick >= 0 && (this.currentTick < this.lastTick || Math.abs(this.currentTick - this.lastTick) > 1);

        if (seeked)
        {
            boolean seekedBackward = targetTick < this.lastTick;
            ParticleState state = this.tickMemory.get(this.currentTick);

            if (!seekedBackward)
            {
                // 正向大跨度跳转直接推进现有发射器，避免播放开始时无意义地从头重建一次
                this.ensureEmitter();
            }
            else if (state != null && state.effectId != null && state.effectId.equals(this.getEffectId()))
            {
                this.stopEmitter();
                this.ensureEmitter();
                this.filmWasPlaying = state.wasPlaying;

                // 恢复发射器的播放/暂停状态以匹配寻帧目标时刻
                if (this.emitter != null)
                {
                    if (this.filmWasPlaying)
                    {
                        this.emitter.resume();
                        this.lastPaused = false;
                    }
                    else
                    {
                        this.emitter.pause();
                        this.lastPaused = true;
                    }
                }
            }
            else
            {
                this.stopEmitter();
                this.ensureEmitter();
            }

            this.seekEmitterToTimeline(targetTick);
            this.currentTick = targetTick;
        }
        else
        {
            this.ensureEmitter();
        }

        // 记录当前状态到寻帧记忆
        if (this.currentTick >= 0)
        {
            ResourceLocation effectId = this.getEffectId();

            if (effectId != null)
            {
                this.tickMemory.put(this.currentTick, new ParticleState(true, effectId));
            }
        }

        if (this.emitter == null)
        {
            this.lastTick = this.currentTick;
            return;
        }

        // 处理重启关键帧
        boolean restart = this.form.restart.get();
        if (restart && !this.lastRestart)
        {
            this.emitter.stop();
            this.emitter = null;
            this.ensureEmitter();
            resetTriggers();
        }
        this.lastRestart = restart;

        // 重置重启标志，使按钮下次点击再次生效
        if (restart)
        {
            this.form.restart.set(false);
        }

        /*
         * 不在客户端 tick 中重建自然结束的循环发射器。
         * 循环边界、进度余数和触发器状态统一由 render3D() 处理，避免两条控制路径竞争，
         * 造成部分特效首次播放约半秒后被 tick 路径提前从头重建。
         */
        this.lastTick = this.currentTick;
        this.filmWasPlaying = true;
    }

    /**
     * 销毁渲染器时清理资源
     */
    public void cleanup()
    {
        this.stopEmitter();
    }

    /* ===== 触发器辅助 ===== */

    /**
     * 重置触发器状态。
     * 在发射器停止、重启、循环重建时调用。
     */
    private void resetTriggers()
    {
        java.util.Arrays.fill(this.pendingTriggers, false);
        java.util.Arrays.fill(this.lastTriggerValues, false);
        this.lastTriggerCheckTick = Integer.MIN_VALUE;
    }

    /**
     * 消费待发送的触发信号。双通道设计：
     * <ul>
     *   <li>通道 1：UI 手动触发 — 消费 {@code form.manualTriggerPulse} 中的脉冲信号</li>
     *   <li>通道 2：关键帧触发 — 每个新 tick 检查一次 {@code form.trigger0-3}，值为 true 则触发，不修改 form 值</li>
     * </ul>
     * <p>必须在发射器存活时调用（emitter != null && emitter.exists()）。</p>
     */
    private void consumePendingTriggers()
    {
        // 通道 1：消费 UI 手动触发脉冲
        for (int i = 0; i < 4; i++)
        {
            if (this.form.manualTriggerPulse[i])
            {
                this.pendingTriggers[i] = true;
                this.form.manualTriggerPulse[i] = false;
            }
        }

        // 通道 2：关键帧触发 — 每个新 tick 检查一次 form.trigger 值
        // 不修改 form 值，避免干扰关键帧系统
        int tick = this.currentTick >= 0 ? this.currentTick : this.lastRenderTick;

        if (tick != this.lastTriggerCheckTick)
        {
            this.lastTriggerCheckTick = tick;

            boolean current0 = this.form.trigger0.get();
            boolean current1 = this.form.trigger1.get();
            boolean current2 = this.form.trigger2.get();
            boolean current3 = this.form.trigger3.get();

            if (current0 && !this.lastTriggerValues[0]) this.pendingTriggers[0] = true;
            if (current1 && !this.lastTriggerValues[1]) this.pendingTriggers[1] = true;
            if (current2 && !this.lastTriggerValues[2]) this.pendingTriggers[2] = true;
            if (current3 && !this.lastTriggerValues[3]) this.pendingTriggers[3] = true;

            this.lastTriggerValues[0] = current0;
            this.lastTriggerValues[1] = current1;
            this.lastTriggerValues[2] = current2;
            this.lastTriggerValues[3] = current3;
        }

        // 发送所有待发送的触发信号
        for (int i = 0; i < 4; i++)
        {
            if (this.pendingTriggers[i])
            {
                this.emitter.sendTrigger(i);
                this.pendingTriggers[i] = false;
            }
        }
    }

    /**
     * 检查是否有待发送的触发器信号。
     * <p>
     * 在非循环模式下效果播放完毕后调用，用于判断是否需要重建发射器。
     * 检查 UI 手动脉冲和关键帧 trigger 值两个通道。
     * </p>
     */
    private boolean hasPendingTriggerSignal()
    {
        // 检查 UI 手动脉冲
        for (int i = 0; i < 4; i++)
        {
            if (this.form.manualTriggerPulse[i]) return true;
        }

        // 检查关键帧 trigger 值（仅在新 tick 时）
        int tick = this.currentTick >= 0 ? this.currentTick : this.lastRenderTick;

        if (tick != this.lastTriggerCheckTick)
        {
            boolean current0 = this.form.trigger0.get();
            boolean current1 = this.form.trigger1.get();
            boolean current2 = this.form.trigger2.get();
            boolean current3 = this.form.trigger3.get();

            return (current0 && !this.lastTriggerValues[0]) ||
                   (current1 && !this.lastTriggerValues[1]) ||
                   (current2 && !this.lastTriggerValues[2]) ||
                   (current3 && !this.lastTriggerValues[3]);
        }

        return false;
    }
}
