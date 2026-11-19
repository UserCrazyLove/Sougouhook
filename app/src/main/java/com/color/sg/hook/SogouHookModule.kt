package com.color.sg.hook

import com.color.sg.hook.core.FeatureRegistry
import com.color.sg.hook.core.HookLog
import com.color.sg.hook.core.HookScope
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 模块入口类。
 *
 * 类名固定写在 `src/main/resources/META-INF/xposed/java_init.list` 里，
 * 框架加载模块时会实例化本类，再回调下面的生命周期方法。
 *
 * 生命周期（libxposed API 101）：
 * 1. [onModuleLoaded]：模块被注入进程，此时只有进程信息；
 * 2. [onPackageReady]：目标包的 ClassLoader 已就绪，是安装 Hook 的时机；
 * 3. [onSystemServerStarting]：本模块没有 system_server 作用域，只记录日志。
 */
class SogouHookModule : XposedModule() {

    /** onModuleLoaded 里拿到的进程名，后续回调不再提供该信息。 */
    @Volatile
    private var processName: String? = null

    /** 同一进程只安装一次功能。 */
    @Volatile
    private var installed = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        HookLog.attach(this)
        processName = param.processName

        HookLog.i(
            "模块已加载: process=${param.processName}, systemServer=${param.isSystemServer}, " +
                "scope=${HookConfig.TARGET_PACKAGE}, framework=$frameworkName/$frameworkVersion, " +
                "api=$apiVersion, properties=0x${java.lang.Long.toHexString(frameworkProperties)}"
        )

        if (param.isSystemServer && !HookConfig.HANDLE_SYSTEM_SERVER) {
            HookLog.w("system_server 不在作用域内，后续回调只记录日志")
            return
        }
        if (!HookConfig.isTargetProcess(param.processName)) {
            HookLog.w("进程 ${param.processName} 不在作用域内，后续回调只记录日志")
            return
        }
        HookLog.i("进程命中作用域: ${param.processName}，等待目标包就绪")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!HookConfig.isTargetPackage(param.packageName)) {
            HookLog.d("忽略非作用域包: ${param.packageName}")
            return
        }
        val currentProcess = processName ?: param.packageName
        if (!HookConfig.isTargetProcess(currentProcess)) {
            HookLog.d("忽略作用域外的进程: $currentProcess")
            return
        }
        if (installed) {
            HookLog.d("本进程已安装过功能，忽略重复回调: ${param.packageName}")
            return
        }
        installed = true

        HookLog.i("目标包就绪: ${param.packageName}（进程 $currentProcess），开始安装功能")
        val scope = HookScope(
            packageName = param.packageName,
            processName = currentProcess,
            classLoader = param.classLoader,
            module = this,
        )
        FeatureRegistry.installAll(scope)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        HookLog.i("system_server 启动：本模块没有 system_server 作用域，忽略")
    }
}