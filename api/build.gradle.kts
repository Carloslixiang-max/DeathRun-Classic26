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
    replaceToken("{gitCommitHash}", "github-actions")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${project.parent?.property("minecraft.version")}")
    compileOnly("org.jetbrains:annotations:${project.parent?.property("jetbrains.annotations.version")}")
    annotationProcessor("org.jetbrains:annotations:${project.parent?.property("jetbrains.annotations.version")}")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    shadowJar {
        archiveFileName.set("${project.parent?.name}-${project.name}-${project.version}.jar")
    }

    build {
        dependsOn(shadowJar)
    }
}
