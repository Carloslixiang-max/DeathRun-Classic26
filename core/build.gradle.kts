plugins {
    id("java")
    id("net.kyori.blossom") version "1.3.1"
    id("com.gradleup.shadow") version "9.6.1"
}

val manualVersion = "1.4.1-classic26-dev"

project.group = project.parent?.group!!
project.version = project.parent?.version!!

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

blossom {
    replaceToken("{version}", manualVersion)
    replaceToken("{gitBranch}", "classic26-dev")
    replaceToken("{gitCommitHash}", "local-build")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.panda-lang.org/releases/")
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://storehouse.okaeri.eu/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":api"))
    compileOnly("io.papermc.paper:paper-api:${project.parent?.property("minecraft.version")}")
    implementation("dev.rollczi:litecommands-core:${project.parent?.property("litecommands.version")}")
    implementation("dev.rollczi:litecommands-bukkit:${project.parent?.property("litecommands.version")}")
    implementation("eu.okaeri:okaeri-configs-yaml-bukkit:${project.parent?.property("okaeri.configs.version")}")
    implementation("eu.okaeri:okaeri-configs-serdes-bukkit:${project.parent?.property("okaeri.configs.version")}")
    implementation("net.kyori:adventure-api:${project.parent?.property("kyori.adventure.version")}")
    implementation("net.kyori:adventure-text-minimessage:${project.parent?.property("kyori.adventure.version")}")
    implementation("net.kyori:adventure-platform-bukkit:${project.parent?.property("kyori.adventure.platform.version")}")
    implementation("net.kyori:adventure-text-serializer-bungeecord:${project.parent?.property("kyori.adventure.platform.version")}")
    implementation("commons-io:commons-io:${project.parent?.property("apache.commons.io.version")}")
    implementation("net.lingala.zip4j:zip4j:${project.parent?.property("zip4j.version")}")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:${project.parent?.property("worldedit.version")}")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.github.koca2000:NoteBlockAPI:1.6.2")
    compileOnly("org.jetbrains:annotations:${project.parent?.property("jetbrains.annotations.version")}")
    annotationProcessor("org.jetbrains:annotations:${project.parent?.property("jetbrains.annotations.version")}")
}

tasks {
    processResources {
        inputs.property("version", manualVersion)
        expand(inputs.properties)
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    shadowJar {
        archiveFileName.set("DeathRun-${project.version}.jar")
        archiveClassifier.set("")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    build {
        dependsOn(shadowJar)
    }
}
