plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "io.moderne"
description = "JSON-RPC 2.0 client and server library"

dependencies {
    // https://msgpack.org/
    // depends on Jackson 2.18 right now, so we'll introduce when we
    // rev Jackson elsewhere
//    implementation("org.msgpack:jackson-dataformat-msgpack:latest.release")

    api("org.jspecify:jspecify:latest.release")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("io.micrometer:micrometer-core:latest.release")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.17.2")
    // Blackbird generates LambdaMetafactory-backed property accessors so
    // Jackson skips the reflective MethodHandle path. ~1.5-2x on real RPC
    // traffic per a JMH bench replaying a captured trace from `mod run`
    // org.openrewrite.node.migrate.upgrade-node-24 — measurable on the
    // GetObject deserialize path where field counts in nested Maps are high.
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.17.2")
    testImplementation("org.openrewrite:rewrite-test:latest.release")
}

nexusPublishing {
    repositories.getByName("sonatype") {
        nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
        snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
    }
}

tasks.named("test") {
    dependsOn("jar")
}
