import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    kotlin("jvm") version "2.0.20-Beta1"
    kotlin("kapt") version "2.0.20-Beta1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("eclipse")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

group = "cn.esuny"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    kapt("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // 新增依赖
    implementation("com.alibaba.fastjson2:fastjson2:2.0.51")          // JSON处理
    implementation("org.yaml:snakeyaml:2.2")                         // YAML解析
    implementation("org.java-websocket:Java-WebSocket:1.5.6")        // WebSocket客户端

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

tasks {
    runVelocity {
        // Configure the Velocity version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        velocityVersion("3.4.0-SNAPSHOT")
    }

    shadowJar {
        archiveBaseName.set("ChatForward")
        archiveClassifier.set("")
        archiveVersion.set("")

        // 重定位依赖包以避免冲突
        relocate("com.alibaba.fastjson2", "cn.esuny.chatforward.libs.fastjson2")
        relocate("org.yaml.snakeyaml", "cn.esuny.chatforward.libs.snakeyaml")
        relocate("org.java_websocket", "cn.esuny.chatforward.libs.java_websocket")

        // 排除不需要的依赖
        dependencies {
            exclude(dependency("com.velocitypowered:velocity-api:.*"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
        }

        // 最小化JAR文件
        minimize()
    }

    // 使assemble任务依赖于shadowJar
    assemble {
        dependsOn(shadowJar)
    }
}

val targetJavaVersion = 17
kotlin {
    jvmToolchain(targetJavaVersion)
}

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets.main.configure { java.srcDir(generateTemplates.map { it.outputs }) }

project.idea.project.settings.taskTriggers.afterSync(generateTemplates)
project.eclipse.synchronizationTasks(generateTemplates)
