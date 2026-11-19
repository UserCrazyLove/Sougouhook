package com.color.sg.hook.core

import com.color.sg.hook.features.ClipboardUnlimitedFeature
import com.color.sg.hook.features.HideAiWriterFeature
import com.color.sg.hook.features.SmokeTestFeature

/**
 * 功能注册表。
 *
 * 新增一个 Hook 功能时：
 * 1. 在 `features` 包里实现 [HookFeature]；
 * 2. 把它加进下面的 [features] 列表。
 *
 * 已登记功能：隐藏 AI 写作入口、剪贴板解除 300 条上限、链路自检。
 */
object FeatureRegistry {

    private val features: List<HookFeature> = listOf(
        // 隐藏工具栏上的「AI 写作」彩色笔（AiWriteManager.u() → false）
        HideAiWriterFeature(),
        // 剪贴板解除 300 条上限，并把计数文案改成「（x）」
        ClipboardUnlimitedFeature(),
        // 链路自检（只打日志，不装 Hook）
        SmokeTestFeature(),
    )

    /** 已登记功能的 id，界面展示用。 */
    val ids: List<String>
        get() = features.map { it.id }

    /** 已登记功能的 id → 描述，界面展示用。 */
    val entries: List<Pair<String, String>>
        get() = features.map { it.id to it.description }

    /**
     * 依次安装所有启用的功能。
     *
     * 单个功能抛异常只记日志，不影响其它功能，也不会让输入法进程崩溃。
     */
    fun installAll(scope: HookScope) {
        HookLog.i("开始安装功能，共 ${features.size} 个: ${ids.joinToString()}")
        features.forEach { feature ->
            if (!feature.isEnabled()) {
                HookLog.i("功能 ${feature.id} 已禁用，跳过")
                return@forEach
            }
            runCatching { feature.install(scope) }
                .onSuccess { HookLog.i("功能 ${feature.id} 安装完成（${feature.description}）") }
                .onFailure { HookLog.e("功能 ${feature.id} 安装失败", it) }
        }
        HookLog.i("功能处理结束，当前登记的 Hook 数量: ${scope.hookCount}")
    }
}