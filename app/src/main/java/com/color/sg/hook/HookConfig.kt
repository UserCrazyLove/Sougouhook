package com.color.sg.hook

/**
 * 模块的静态配置。
 *
 * 作用域在打包期就已固定：见 `src/main/resources/META-INF/xposed/scope.list`，
 * 这里的 [TARGET_PACKAGE] 必须与之保持一致。
 */
object HookConfig {

    /** 作用域包名：搜狗输入法。 */
    const val TARGET_PACKAGE = "com.sohu.inputmethod.sogouoem"

    /** LSPosed / Xposed 日志标签，同时也是 logcat 标签。 */
    const val LOG_TAG = "SGHook"

    /** 框架日志统一前缀，方便在日志页里过滤。 */
    const val LOG_PREFIX = "[SGHook] "

    /**
     * 需要注入的进程白名单。
     *
     * 空集合 = 包名本身及其所有 `:子进程` 都生效；
     * 确定目标进程后可以收紧，例如
     * `setOf(TARGET_PACKAGE, "$TARGET_PACKAGE:sandbox")`。
     */
    val TARGET_PROCESSES: Set<String> = emptySet()

    /** 是否处理 system_server 进程。本模块只关心输入法自己的进程。 */
    const val HANDLE_SYSTEM_SERVER = false

    /** 包名是否在作用域内。 */
    fun isTargetPackage(packageName: CharSequence?): Boolean =
        packageName?.toString() == TARGET_PACKAGE

    /** 进程名是否属于作用域。 */
    fun isTargetProcess(processName: String?): Boolean {
        if (processName.isNullOrEmpty()) return false
        if (TARGET_PROCESSES.isNotEmpty()) return processName in TARGET_PROCESSES
        return processName == TARGET_PACKAGE || processName.startsWith("$TARGET_PACKAGE:")
    }

    /** 进程策略的可读描述，界面和日志共用。 */
    fun describeProcesses(): String =
        if (TARGET_PROCESSES.isEmpty()) {
            "全部进程（$TARGET_PACKAGE 及其 : 子进程）"
        } else {
            TARGET_PROCESSES.joinToString()
        }
}