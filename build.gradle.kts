plugins {
    id("java")
}

group = "com.paulorchard.islandcraft"
version = "0.1.0"

val hytaleServerVersion = "0.7.0"
val modsDir = file("${System.getenv("APPDATA")}/Hytale/UserData/Mods")

repositories {
    mavenCentral()
    maven {
        name = "Hytale"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:$hytaleServerVersion")
    // Water of Arrakis is an optional dependency (see WaterBridge): compiled against its built jar, never packed into this one.
    // Build that mod first (its gradlew jar); -PwaterJar=<path> points at another copy.
    compileOnly(files(providers.gradleProperty("waterJar").orElse("../WaterOfArrakis/build/libs/IslandCraft-WaterOfArrakis-0.1.0.jar")))
    testImplementation("com.hypixel.hytale:Server:$hytaleServerVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("IslandCraft-EquipmentOfArrakis")
}

tasks.register<Copy>("deployMod") {
    group = "hytale"
    description = "Copies the built jar into the Hytale Mods folder, replacing any earlier build of this mod."

    from(tasks.jar)
    into(modsDir)

    doFirst {
        delete(fileTree(modsDir) {
            include("IslandCraft-EquipmentOfArrakis-*.zip", "IslandCraft-EquipmentOfArrakis-*.jar")
        })
    }
}
