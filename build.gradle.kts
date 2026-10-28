import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.2"
}

group = "dev.akira"
version = "0.0.1-SNAPSHOT"
description = "PayFlow"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot dependency version management
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:4.1.2")
    )

    // Application
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // HTMX Spring Boot integration (Thymeleaf variant)
    implementation("io.github.wimdeblauwe:htmx-spring-boot-thymeleaf:5.2.0")

    // Database migrations and driver
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Bytecode transformation
    implementation("org.ow2.asm:asm:9.11.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")

    // Mockito Java agent
    testRuntimeOnly("org.mockito:mockito-core")

    // Testcontainers
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:1.21.4")
}

// Isolated configuration for the Mockito Java agent
val mockitoAgent by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    mockitoAgent("org.mockito:mockito-core:5.18.0")
}

// Test execution, logging, and Maven-style summary
tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    // Attach the Mockito Java agent to the test JVM (Modern syntax)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-javaagent:${mockitoAgent.singleFile.absolutePath}",
                "-Xshare:off"
            )
        }
    )

    // Print a summary after the root test suite finishes
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit

        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
            if (desc.parent == null) {
                println()
                println("========================================")
                println("              TEST SUMMARY")
                println("========================================")
                println("Total tests : ${result.testCount}")
                println("Passed      : ${result.successfulTestCount}")
                println("Failed      : ${result.failedTestCount}")
                println("Skipped     : ${result.skippedTestCount}")
                println("========================================")
            }
        }

        override fun beforeTest(testDescriptor: TestDescriptor) = Unit
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
    })
}

// Generate the JaCoCo report after tests
tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Transform compiled record constructors to inject @NotNull checks
val transformRecordNullability by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Injects @NotNull checks into record constructors."

    dependsOn(tasks.named("compileJava"))

    classpath = sourceSets.main.get().runtimeClasspath

    mainClass.set(
        "dev.akira.payflow.common.domain.validation.RecordNullabilityTransformer"
    )

    // Using the file path directly without eager .get()
    args(
        layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath
    )
}

// Run the transformation before test compilation and execution
tasks.named("compileTestJava") {
    dependsOn(transformRecordNullability)
}

tasks.named("test") {
    dependsOn(transformRecordNullability)
}
