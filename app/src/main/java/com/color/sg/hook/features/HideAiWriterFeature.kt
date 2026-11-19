package com.color.sg.hook.features

import com.color.sg.hook.core.HookFeature
import com.color.sg.hook.core.HookScope
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 隐藏搜狗输入法（OPPO 定制版）工具栏上的「AI 写作」入口 —— 也就是那支彩色渐变笔。
 *
 * 目标版本（sg.apk，5 个 dex）里的显示链路：
 * ```
 * PersonalizedContentView.updatePersonalizedContent(Z)
 *     ├─ AiIcon 分支：mAiImage.setVisibility(VISIBLE)，背景取 mRootFunctionItem.q 的 drawable
 *     │  contentDescription = string/openplatform_ai_writer（zh-CN "AI写作"）
 *     ├─ AiNone 分支：mAiImage.setVisibility(GONE)，收起 AI 气泡
 *     └─ 类型来自 d20.c()（读缓存字段，缓存由 d20.g() 写入）
 * d20.g()  ←  AiWriteManager.u()Z
 * u() = !e20.b() && 入口开关(Settings.Secure "aiwriter_input_entrance_enabled") && ...
 * e20.b() 读 SP "oppo_ai_writer_data" → "oppo_close_ai_writer"（官方「关闭 AI 写作」开关）
 * ```
 *
 * 隐藏按三层依次尝试，任一层生效即可：
 * 1. 总开关 `AiWriteManager.u()` 恒 false → `d20.g()` 直接置 AiNone；
 * 2. 官方关闭开关 `e20.b()` 恒 true；
 * 3. 界面兜底：`updatePersonalizedContent(Z)` 执行后，若类型仍是 AiIcon 就把 mAiImage 置 GONE。
 *
 * 注意：Hook 在 onPackageReady 阶段安装，早于任何键盘视图创建，所以不存在「缓存已经算过」的时序问题。
 */
class HideAiWriterFeature : HookFeature {

    override val id: String = "hide-ai-writer"

    override val description: String = "隐藏 AI 写作入口（彩色笔 / PersonalizedContentType.AiIcon）"

    override fun install(scope: HookScope) {
        if (hookMasterSwitch(scope)) return
        if (hookCloseSwitch(scope)) return
        hookIconView(scope)
    }

    /** 第一层：AiWriteManager.u()Z → false。 */
    private fun hookMasterSwitch(scope: HookScope): Boolean {
        val cls = scope.findClass(MASTER_CLASS) ?: return false
        val method = noArgMethod(cls, "u")
        if (method == null) {
            scope.logError("$MASTER_CLASS#u() 不存在，可能是版本差异")
            return false
        }
        // Hooker 返回类型是 Object，Boolean 会被框架写回 Z 返回值
        scope.hook(method) { java.lang.Boolean.FALSE }
        scope.log("AI 写作：已接管总开关 $MASTER_CLASS#u()")
        return true
    }

    /** 第二层：e20.b()Z → true（它自带的「关闭 AI 写作」语义）。 */
    private fun hookCloseSwitch(scope: HookScope): Boolean {
        val cls = scope.findClass(CLOSE_SWITCH_CLASS) ?: return false
        val method = noArgMethod(cls, "b")
        if (method == null) {
            scope.logError("$CLOSE_SWITCH_CLASS#b() 不存在，可能是版本差异")
            return false
        }
        scope.hook(method) { java.lang.Boolean.TRUE }
        scope.log("AI 写作：已接管关闭开关 $CLOSE_SWITCH_CLASS#b()")
        return true
    }

    /** 第三层：类型仍是 AiIcon 时，直接把那只笔隐藏掉。 */
    private fun hookIconView(scope: HookScope) {
        val viewCls = scope.findClass(VIEW_CLASS) ?: return
        val typeCls = scope.findClass(TYPE_ENUM_CLASS) ?: return
        val aiIcon = runCatching { typeCls.getField("AiIcon").get(null) }.getOrNull()
        if (aiIcon == null) {
            scope.logError("$TYPE_ENUM_CLASS#AiIcon 取不到，放弃界面层兜底")
            return
        }
        val repoField = accessibleField(viewCls, REPO_FIELD) ?: return
        val imageField = accessibleField(viewCls, ICON_IMAGE_FIELD) ?: return
        val typeGetter = runCatching {
            repoField.type.getDeclaredMethod("c").apply { isAccessible = true }
        }.getOrNull() ?: return
        val update = runCatching {
            viewCls.getDeclaredMethod("updatePersonalizedContent", java.lang.Boolean.TYPE)
        }.getOrNull() ?: return

        scope.hook(update) { chain ->
            val result = chain.proceed()
            runCatching {
                val self = chain.thisObject
                val type = typeGetter.invoke(repoField.get(self))
                if (type === aiIcon) {
                    (imageField.get(self) as? android.view.View)?.visibility = android.view.View.GONE
                }
            }.onFailure { scope.logError("隐藏 AI 图标失败", it) }
            result
        }
        scope.log("AI 写作：已挂界面兜底层 $VIEW_CLASS#updatePersonalizedContent(Z)")
    }

    private fun noArgMethod(cls: Class<*>, name: String): Method? =
        runCatching { cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 } }.getOrNull()

    private fun accessibleField(cls: Class<*>, name: String): Field? =
        runCatching { cls.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()

    private companion object {
        const val MASTER_CLASS = "com.sogou.bu.oem.oppo.function.ai.AiWriteManager"
        /** 混淆后的默认包类名，改动版本时优先改这里。 */
        const val CLOSE_SWITCH_CLASS = "e20"
        const val VIEW_CLASS = "com.sogou.bu.oem.oppo.function.ui.PersonalizedContentView"
        const val TYPE_ENUM_CLASS = "com.sogou.bu.oem.oppo.function.data.PersonalizedContentType"
        const val REPO_FIELD = "mPersonalizedDataRepository"
        const val ICON_IMAGE_FIELD = "mAiImage"
    }
}