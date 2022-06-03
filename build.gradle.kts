import java.net.URI

plugins {
    application
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "indi.goldenwater"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        name = "spigot-repo"
        url = URI("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.21")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    compileOnly("com.github.wendykierp:JTransforms:3.1")

    compileOnly("net.kyori:text-adapter-bukkit:3.0.6")

    compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    val fatJar = register<Jar>("fatJar") {
        dependsOn.addAll(
            listOf(
                "compileJava",
                "compileKotlin",
                "processResources"
            )
        )
//        archiveClassifier.set("standalone") // Naming the jar
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) }

        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath
            .get()
            .map { if (it.isDirectory) it else zipTree(it) } +
                sourcesMain.output
        from(contents)
    }
    build {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }

    processResources {
        val props = mutableMapOf<String, String>()
        props["version"] = version as String

        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

application {
    mainClass.set("indi.goldenwater.chaosmusicplayer.ChaosMusicPlayerKt")
}