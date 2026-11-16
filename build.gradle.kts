plugins {
    java
    id("net.neoforged.moddev") version "2.0.148"
}

val modId = providers.gradleProperty("mod_id").get()
val modVersion = providers.gradleProperty("mod_version").get()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val neoVersion = providers.gradleProperty("neo_version").get()

group = "dev.compactmods.$modId"
version = modVersion

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val moduleNames = file("src/modules").listFiles()
    ?.filter { it.isDirectory }
    ?.map { it.name }
    ?.sorted()
    .orEmpty()

val common = sourceSets.create("common")
common.java.srcDirs("src/common/java")

val neoforge = sourceSets.create("neoforge")
neoforge.java.srcDirs("src/neoforge/java")

val modules = moduleNames.map { name ->
    val module = sourceSets.create(name)
    module.java.srcDirs("src/modules/$name/java")
    module
}

sourceSets.main {
    java {
        srcDir("src/main/java")
        srcDir(common.java)
        srcDir(neoforge.java)
        modules.forEach { module -> srcDir(module.java) }
    }
}

repositories {
    maven("https://maven.pkg.github.com/compactmods/compactmachines") {
        name = "CompactMachinesPackages"
        content {
            includeGroup("dev.compactmods")
        }
        credentials {
            username = System.getenv("MAVEN_USERNAME")
            password = System.getenv("MAVEN_KEY")
        }
    }
}

val compactMachinesMod = fileTree("libs") { include("compactmachines*.jar") }
val compactMachinesApi = fileTree("libs") { include("core-api*.jar") }

neoForge {
    version = neoVersion

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

neoForge.addModdingDependenciesTo(common)
neoForge.addModdingDependenciesTo(neoforge)
modules.forEach { module -> neoForge.addModdingDependenciesTo(module) }

dependencies {
    implementation(compactMachinesMod)
    compileOnly(compactMachinesApi)
}

tasks.processResources {
    val expansions = mapOf(
        "mod_version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "neo_version" to neoVersion,
    )

    inputs.properties(expansions)
    filesMatching("META-INF/neoforge.mods.toml") { expand(expansions) }
}
