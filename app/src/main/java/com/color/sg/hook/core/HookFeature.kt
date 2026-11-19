package com.color.sg.hook.core

/**
 * 一个可独立开关的 Hook 功能单元。
 *
 * 目标包加载完成后，[FeatureRegistry] 会依次调用已注册功能的 [install]。
 * 新增功能：在 `features` 包里实现本接口，然后到 [FeatureRegistry] 登记。
 */
interface HookFeature {

    /** 唯一标识，用于日志与后续的开关存储。 */
    val id: String

    /** 人类可读描述，日志和界面共用。 */
    val description: String

    /** 是否在当前作用域里安装，默认安装。 */
    fun isEnabled(): Boolean = true

    /** 安装功能。调用时包名 / 进程已确认在作用域内。 */
    fun install(scope: HookScope)
}