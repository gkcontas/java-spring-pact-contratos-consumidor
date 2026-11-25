rootProject.name = "consumer-driven-contracts"

// One provider, two consumers. Each one is a separate Spring Boot application with its
// own jar: they share a build only so that the contracts can travel between them in a
// single checkout. Nothing in the source of one module is visible to another.
include("catalog-service", "order-service", "shipping-service")
