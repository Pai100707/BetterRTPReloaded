plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.tcoded.com/releases")
    maven("https://nexus.hc.to/content/repositories/pub_releases")
    maven("https://maven.enginehub.org/repo/")
    maven("https://raw.githubusercontent.com/FabioZumbi12/RedProtect/mvn-repo/")
    maven("https://ci.ender.zone/plugin/repository/everything/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.essentialsx.net/releases/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.william278.net/releases")
    maven("https://repository.minecodes.pl/releases") {
        content { includeGroup("pl.minecodes.plots") }
    }
    mavenLocal()
}

dependencies {
    api(libs.io.papermc.paperlib)
    api(libs.xyz.xenondevs.particle)
    api(libs.com.tcoded.folialib)

    compileOnly(libs.org.spigotmc.spigot.api)
    compileOnly(libs.net.milkbowl.vault.vaultapi)
    compileOnly(libs.com.sk89q.worldguard.worldguard.bukkit)
    compileOnly(libs.br.net.fabiozumbi12.redprotect.redprotect.core)
    compileOnly(libs.br.net.fabiozumbi12.redprotect.redprotect.spigot.v1.v13)
    compileOnly(libs.com.massivecraft.factions)
    compileOnly(libs.com.github.angeschossen.landsapi)
    compileOnly(libs.com.comphenix.protocol.protocollib)
    compileOnly(libs.com.github.townyadvanced.towny)
    compileOnly(libs.com.github.techfortress.griefprevention)
    compileOnly(libs.com.github.cryptomorin.kingdoms) {
        exclude(group = "*", module = "*")
    }
    compileOnly(libs.com.github.bloodmc.griefdefenderapi)
    compileOnly(libs.com.github.superronancraft.pueblos)
    compileOnly(libs.org.projectlombok.lombok)
    annotationProcessor(libs.org.projectlombok.lombok)
    compileOnly(libs.net.essentialsx.essentialsx) {
        exclude(group = "org.bstats", module = "bstats-bukkit")
    }
    compileOnly(libs.com.github.saberllc.saber.factions) {
      isTransitive = false
    }
    compileOnly(libs.me.clip.placeholderapi)
    compileOnly(libs.net.william278.huskclaims.huskclaims.bukkit)
    compileOnly(libs.net.william278.husktowns.husktowns.bukkit)
    compileOnly(libs.pl.minecodes.plots.plugin.api)
    compileOnly(libs.com.github.whipdevelopment.crashclaim)

    // was `system(...)` – not valid in Gradle
    // compileOnly(libs.com.dansplugins.factionsystem.factionsbridge)
    // compileOnly(libs.com.bekvon.bukkit.residence.residence)

    compileOnly(fileTree("lib") { include("*.jar") })
}

configurations.all {
    exclude(group = "org.bstats")
    exclude(group = "de.keyle")
    exclude(group = "net.dv8tion")
    exclude(group = "me.rayzr522")
    exclude(group = "com.jagrosh")
    exclude(group = "org.spongepowered")

    resolutionStrategy.eachDependency {
        if (requested.group == "com.sk89q.worldedit") useVersion("7.2.15")
        if (requested.group == "com.sk89q.worldguard") useVersion("7.0.9")
    }
}

group = "me.SuperRonanCraft"
version = "3.6.13"
description = "BetterRTPReloaded"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("io.papermc.lib", "me.SuperRonanCraft.BetterRTP.libs.paperlib")
    relocate("com.tcoded.folialib", "me.SuperRonanCraft.BetterRTP.libs.folialib")
    relocate("xyz.xenondevs.particle", "me.SuperRonanCraft.BetterRTP.libs.particle")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}