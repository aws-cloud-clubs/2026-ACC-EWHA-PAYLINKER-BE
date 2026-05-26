import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    // gradleup fork = Gradle 9.x 호환 (원본 johnrengelman 은 Gradle 9 와 incompatible)
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    // AWS Lambda runtime
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.4")

    // AWS SDK v2 (Lambda cold start 최적화를 위해 url-connection-client 사용)
    implementation(platform("software.amazon.awssdk:bom:2.32.0"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:ses")
    implementation("software.amazon.awssdk:url-connection-client")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn("shadowJar")
}
