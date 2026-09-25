import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import java.lang.String.format
import java.lang.String.valueOf

plugins {
    id("java")
    id("net.kyori.blossom") version "1.3.1"
    id("com.palantir.git-version") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

val versionDetails: Closure<VersionDetails> by extra
fun projectVersion(): String = if (versionDetails().branchName == "ver/latest")
    valueOf(project.version) else format("%s (git/%s)", project.version, versionDetails().gitHash)

project.group = project.parent?.group!!
project.version = project.parent?.version!!

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

blossom {
    replaceToken("{version}", projectVersion())
    replaceToken("{gitBranch}", versionDetails().branchName)
    replaceToken("{gitCommitHash}", versionDetails().gitHashFull)
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
