plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Consumer of catalog-service"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("au.com.dius.pact.consumer:junit5:${rootProject.extra["pactVersion"]}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    enabled = false
}

// The contract is a build output of the consumer, like a jar: written by the Pact tests,
// copied by the provider build, published to the broker by the pipeline.
val pactsDir = layout.buildDirectory.dir("pacts")
tasks.test {
    systemProperty("pact.rootDir", pactsDir.get().asFile.absolutePath)
    outputs.dir(pactsDir)
}
