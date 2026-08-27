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
    // Leaf API - see the compileOnly dependency below for why this replaced paper-api.
    maven("https://maven.leafmc.one/snapshots/")
}

dependencies {
    // Compiled directly against the user's actual production server (a Leaf fork of Paper,
    // Minecraft 1.21.11 - "Leaf-1.21.11-R0.1-SNAPSHOT") instead of the newest Paper API
    // (26.2), after a production crash (NoSuchMethodError on an Adventure bridge method -
    // see DamageFeedback's history/PLAN.md) traced to compiling against a newer API than
    // what's actually running. Leaf is API-compatible with Paper (same org.bukkit/
    // io.papermc.paper classes), so this is otherwise a drop-in replacement for
    // io.papermc.paper:paper-api at this version.
    compileOnly("cn.dreeam.leaf:leaf-api:1.21.11-R0.1-SNAPSHOT")

    // MythicMobs integration is soft-depend - guarded at runtime behind an
    // isPluginEnabled("MythicMobs") check in mythicmobs/MythicMobsBridge, the
    // only class allowed to import this API.
    compileOnly("io.lumine:Mythic-Dist:5.10.0")

    // Bundled with the Paper/Leaf server runtime and used by ValhallaMmoBulkImporter to
    // read their items.json - not shaded. Version pinned to whatever 1.21.11 actually
    // bundles (checked via a real runServer boot), same reasoning as the leaf-api switch
    // above - a newer Gson than what's on the runtime classpath risks the same class of
    // NoSuchMethodError.
    compileOnly("com.google.code.gson:gson:2.13.2")

    // Storage: shaded into the plugin jar, Paper does not provide these on the plugin classpath
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")

    testImplementation("cn.dreeam.leaf:leaf-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("com.google.code.gson:gson:2.13.2")

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
        // Matches the user's actual production server version (Leaf, Minecraft 1.21.11) -
        // downloads a real PaperMC build for local testing. Leaf itself isn't downloadable
        // through this plugin, but real Paper at the same Minecraft version is much closer
        // to production than the newest available Paper build was.
        minecraftVersion("1.21.11")
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
