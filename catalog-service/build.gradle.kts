plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Provider: product catalog and stock reservations"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("au.com.dius.pact.provider:junit5spring:${rootProject.extra["pactVersion"]}")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // The change matrix drives the Pact verification through the launcher API.
    testImplementation("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${rootProject.extra["testcontainersVersion"]}")
    }
}

tasks.jar {
    enabled = false
}

// In this repository the consumers are sibling modules, so their contracts can be
// copied straight from their build output. In separate repositories the broker is the
// only channel (see PactBrokerWorkflowIT); the verification code is the same.
val consumerPacts = layout.buildDirectory.dir("consumer-pacts")
val collectConsumerPacts by tasks.registering(Sync::class) {
    dependsOn(":order-service:test", ":shipping-service:test")
    from(rootProject.layout.projectDirectory.dir("order-service/build/pacts"))
    from(rootProject.layout.projectDirectory.dir("shipping-service/build/pacts"))
    into(consumerPacts)
}

tasks.test {
    dependsOn(collectConsumerPacts)
    inputs.dir(consumerPacts)
    systemProperty("consumer.pacts.dir", consumerPacts.get().asFile.absolutePath)
    systemProperty("broker.report", layout.buildDirectory.file("reports/broker-workflow.txt").get().asFile.absolutePath)
    // Lets a pipeline run the broker verification through Gradle:
    // ./gradlew :catalog-service:test --tests '*CatalogBrokerVerification' -Dpactbroker.url=...
    // catalog.mutation is the test-only switch that simulates a provider change (see
    // ResponseMutation); forwarding it lets the same run play a breaking provider build.
    System.getProperties().stringPropertyNames()
        .filter {
            it.startsWith("pactbroker.") || it.startsWith("pact.provider.") || it.startsWith("pact.verifier.") ||
                it == "catalog.mutation"
        }
        .forEach { systemProperty(it, System.getProperty(it)) }
    systemProperty("matrix.report", layout.buildDirectory.file("reports/contract-change-matrix.txt").get().asFile.absolutePath)
}
