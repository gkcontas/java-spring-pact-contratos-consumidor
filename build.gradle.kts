plugins {
    id("org.springframework.boot") version "3.5.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.gkcontas"
    version = "1.0.0"
}

description = "Consumer-driven contract testing with Pact: two consumers declare what they use " +
    "from a provider, the provider is verified against those contracts, and a Pact Broker " +
    "records versions, verification results and deployments to answer can-i-deploy"

val pactVersion by extra("4.7.7")
val testcontainersVersion by extra("1.21.4")

subprojects {
    repositories {
        mavenCentral()
    }

    // Pact JVM 4.7 is compiled with Kotlin 2.3 and declares kotlin-stdlib 2.3.21. The
    // Spring Boot BOM manages kotlin-stdlib too (1.9.x in Boot 3.5) and the
    // dependency-management plugin would silently downgrade it. Pinning the property the
    // BOM reads keeps the runtime the library was compiled against.
    extra["kotlin.version"] = "2.3.21"

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
