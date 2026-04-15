plugins {
    java
    application
    kotlin("jvm")
}

dependencies {
    implementation("com.github.hbci4j:hbci4j-core:3.1.88")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

configurations.all {
    resolutionStrategy {
        force("org.slf4j:slf4j-api:2.0.9")
    }
}

application {
    mainClass.set("de.mybudgets.sync.BbbankSyncKt")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "de.mybudgets.sync.BbbankSyncKt")
    }
    from(configurations.runtimeClasspath.get().files.map { file ->
        if (file.isDirectory) file else zipTree(file)
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
