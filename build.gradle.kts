plugins {
    java
    id("net.neoforged.moddev") version "2.0.148"
    id("com.diffplug.spotless") version "8.10.2"
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

val dotEnv: Map<String, String> = layout.projectDirectory.file(".env").asFile.let { file ->
    if (!file.isFile) {
        emptyMap()
    } else {
        file.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .associate { line ->
                line.substringBefore('=').trim() to
                    line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
            }
    }
}

fun credential(property: String, vararg names: String): String? =
    providers.gradleProperty(property).orNull
        ?: names.firstNotNullOfOrNull { name ->
            dotEnv[name]?.takeIf { it.isNotEmpty() }
                ?: System.getenv(name)?.takeIf { it.isNotEmpty() }
        }

repositories {
    maven("https://maven.pkg.github.com/compactmods/compactmachines") {
        name = "CompactMachinesPackages"
        content {
            // includeGroup is an exact match, so the machine packages need naming too.
            includeGroup("dev.compactmods")
            includeGroup("dev.compactmods.compactmachines")
        }
        credentials {
            username = credential("gpr.user", "MAVEN_USERNAME", "GITHUB_USER")
            password = credential("gpr.key", "MAVEN_KEY", "GITHUB_TOKEN")
        }
    }

    mavenLocal {
        content {
            includeGroup("dev.compactmods")
            includeGroup("dev.compactmods.compactmachines")
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

val compactMachinesVersion =
    providers.gradleProperty("compactmachines_version").getOrElse("7.0.81")

val devDeps = configurations.create("devDeps") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    devDeps("dev.compactmods.compactmachines:compactmachines-neoforge:$compactMachinesVersion")
}

val ensureDevDeps = tasks.register("ensureDevDeps") {
    group = "build"
    description = "Downloads the Compact Machines dev jars into libs/ when they are missing."

    val libsDir = layout.projectDirectory.dir("libs")
    val downloaded = devDeps.incoming.artifactView { isLenient = true }.files
    val modCoordinate =
        "dev.compactmods.compactmachines:compactmachines-neoforge:$compactMachinesVersion"

    doLast {
        fun jarsWithPrefix(prefix: String) =
            project.fileTree(libsDir) { include("$prefix*.jar") }.files

        fun unpackApi(modJar: File): File? {
            val nested =
                project.zipTree(modJar)
                    .matching { include("META-INF/jarjar/*core-api*.jar") }
                    .files
                    .firstOrNull() ?: return null

            val target = libsDir.file("core-api-$compactMachinesVersion.jar").asFile
            target.parentFile.mkdirs()
            nested.copyTo(target, overwrite = true)
            return target
        }

        val modJar =
            jarsWithPrefix("compactmachines").minByOrNull { it.name }
                ?: downloaded.files.firstOrNull {
                    it.name.startsWith("compactmachines") && it.name.endsWith(".jar")
                }
                ?: throw GradleException(
                    "No compactmachines*.jar in libs/, and $modCoordinate could not be downloaded. " +
                        "Version $compactMachinesVersion has to exist in the " +
                        "CompactMachinesPackages repository on GitHub Packages, so check that it " +
                        "is a released 1.21.1 build, that the network is reachable, and that " +
                        "MAVEN_USERNAME/MAVEN_KEY (or GITHUB_USER/GITHUB_TOKEN) are set with the " +
                        "read:packages scope."
                )

        if (modJar.parentFile != libsDir.asFile) {
            modJar.copyTo(libsDir.file(modJar.name).asFile, overwrite = true)
            logger.lifecycle("Downloaded ${modJar.name} into libs/")
        }

        if (jarsWithPrefix("core-api").isEmpty()) {
            val apiJar =
                unpackApi(modJar)
                    ?: throw GradleException(
                        "No core-api*.jar in libs/, and ${modJar.name} contains no nested Core API " +
                            "jar to unpack. Put the core-api jar matching $compactMachinesVersion " +
                            "into libs/ manually."
                    )
            logger.lifecycle("Extracted ${apiJar.name} from ${modJar.name} into libs/")
        }
    }
}

tasks.matching { it.name in setOf("compileJava", "runClient", "runServer", "build") }
    .configureEach { dependsOn(ensureDevDeps) }

spotless {
    java {
        googleJavaFormat("1.28.0").skipJavadocFormatting()
        removeUnusedImports()
        importOrder()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
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
