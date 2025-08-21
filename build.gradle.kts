plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
}

group = "org.nexus.ftc"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.9.0")

    // Add any other dependencies your project needs
}

application {
    mainClass.set("org.nexus.ftc.replay.ReplayViewerLauncher")
}

javafx {
    version = "17.0.2"
    modules("javafx.controls", "javafx.fxml") // Added javafx.fxml here
}

// Optional: Configure jar task to create a fat jar with all dependencies
tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.nexus.ftc.replay.ReplayViewerLauncher"
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}