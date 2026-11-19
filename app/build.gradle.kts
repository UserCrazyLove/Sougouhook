plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.color.sg"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.color.sg"
        // 26 = libxposed API 要求的下限，也是 LSPosed 常见支持范围
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // 只裁剪不优化：optimize/obfuscate 曾把静态初始化路径改坏，导致钩子静默不装
            optimization {
                enable = true
                packageScope = setOf("kotlin.**", "kotlinx.**")
            }
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            // Xposed/LSPosed 模块元数据必须原样进包，不能被依赖的 META-INF 规则裁掉
            merges += "META-INF/xposed/*"
            // 依赖里的许可证等噪音不打包
            excludes += "META-INF/*.txt"
            excludes += "META-INF/*.md"
            excludes += "META-INF/versions/**"
        }
    }
}

dependencies {
    // 运行时由 Xposed 框架提供，绝对不能打包进 APK
    compileOnly(libs.libxposed.api)

    // 纯 Hook 模块没有任何界面，不要再用 androidx/appcompat/material：
    // 它们会把 resources.arsc 撑到 1MB、dex 里塞进整套 UI 组件
}