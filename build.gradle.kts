plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "8.3.11"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // MythicMobs API
    maven("https://mvn.lumine.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // MythicMobs integration is soft-depend - guarded at runtime behind an
    // isPluginEnabled("MythicMobs") check in mythicmobs/MythicMobsBridge, the
    // only class allowed to import this API.
    compileOnly("io.lumine:Mythic-Dist:5.10.0")

    // Storage: shaded into the plugin jar, Paper does not provide these on the plugin classpath
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        // org.sqlite is deliberately NOT relocated - its native library JNI
        // symbols are tied to the org.sqlite.* class names; see PurrtechOrders
        // for the UnsatisfiedLinkError this caused when that was tried.
        relocate("com.zaxxer.hikari", "eu.purrtech.purrtechPVE.libs.hikari")
        relocate("org.slf4j", "eu.purrtech.purrtechPVE.libs.slf4j")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
