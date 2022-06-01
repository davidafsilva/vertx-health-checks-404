import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.21"
    application
}

group = "pt.davidafsilva.jvm.vertx"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    val vertxVersion = "3.9.13"
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation( "io.vertx:vertx-web:$vertxVersion")
    implementation( "io.vertx:vertx-web-api-contract:$vertxVersion")
    implementation( "io.vertx:vertx-health-check:$vertxVersion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val jvmTargetVersion = "17"

plugins.withType<JavaPlugin> {
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(jvmTargetVersion))
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = jvmTargetVersion
    }
}

application {
    mainClass.set("$group.ApplicationKt")
}
