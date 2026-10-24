plugins {
    java
}

group = "fr.chunkrandom"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Dernière build de Paper 26.2
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("ChunkRandomBlock")
}

// Le plugin n'a aucune dépendance à embarquer : shadowJar produit simplement le jar du plugin.
// (Permet de garder la commande « gradle shadowJar » du workflow GitHub.)
tasks.register("shadowJar") {
    group = "build"
    description = "Construit le jar du plugin (build/libs)."
    dependsOn(tasks.jar)
}
