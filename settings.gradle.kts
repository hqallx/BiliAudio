pluginManagement {
    repositories {
        // 官方仓库优先：CI（GitHub ubuntu runner，国外）访问最可靠
        google()
        mavenCentral()
        gradlePluginPortal()
        // 阿里云镜像兜底：官方偶发故障/国内构建加速时接管。
        // gradle-plugin 镜像专门同步 Gradle Plugin Portal，确保 KSP 等
        // 插件 marker artifact 元数据完整（已验证 1.9.21-1.0.15 可达）。
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://jitpack.io")
    }
}

rootProject.name = "BiliAudio"
include(":app")
