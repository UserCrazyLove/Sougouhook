package com.color.sg.hook.features

import com.color.sg.hook.HookConfig
import com.color.sg.hook.core.HookFeature
import com.color.sg.hook.core.HookScope

/**
 * 冒烟自检功能：只打印日志，不安装任何 Hook。
 *
 * 用来验证「模块已启用 + 作用域正确 + 已注入搜狗输入法进程」这条链路。
 *
 * 编写真实功能时可以把本文件当模板：
 * ```
 * override fun install(scope: HookScope) {
 *     val cls = scope.findClass("com.sohu.inputmethod.sogouoem.xxx.Yyy") ?: return
 *     val method = cls.getDeclaredMethod("zzz", /* 参数类型 */)
 *     scope.hook(method) { chain ->
 *         // 前置处理：chain.getArgs()
 *         val result = chain.proceed()
 *         // 后置处理
 *         result
 *     }
 * }
 * ```
 *
 * 当前模块只搭基础框架，没有实现任何真实 Hook。
 */
class SmokeTestFeature : HookFeature {

    override val id: String = "smoke-test"

    override val description: String = "链路自检：只记录注入信息，不安装 Hook"

    override fun install(scope: HookScope) {
        scope.log(
            "自检: package=${scope.packageName}, process=${scope.processName}, " +
                "scope=${HookConfig.TARGET_PACKAGE}, hooks=${scope.hookCount}"
        )
    }
}