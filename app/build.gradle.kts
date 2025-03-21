/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.13/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
    // Apply JaCoCo plugin for test coverage
    jacoco
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
    
    // HTTP Client for API requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    
    // Mockito for mocking in tests
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0")
    
    // MockWebServer for testing HTTP requests
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    // Lombok for reducing boilerplate code
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
    
    // Logging with SLF4J and Logback
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.12.0")
    implementation("io.micrometer:micrometer-registry-jmx:1.12.0")
    
    // Resilience4j for error handling, retries, and circuit breakers
    implementation("io.github.resilience4j:resilience4j-core:2.1.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.1.0")
    implementation("io.github.resilience4j:resilience4j-all:2.1.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // Define the main class for the application.
    mainClass = "org.example.App"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    
    // Enable JaCoCo test coverage
    finalizedBy(tasks.jacocoTestReport)
}

// Configure JaCoCo test coverage
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

// This ensures Lombok annotations are processed correctly
tasks.withType<JavaCompile> {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}
