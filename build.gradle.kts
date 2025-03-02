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

    compileOnly("io.micrometer:micrometer-core:latest.release")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.17.+")

    implementation("org.openrewrite:rewrite-properties:latest.release")
    implementation("org.openrewrite:rewrite-json:latest.release")

    testImplementation("org.openrewrite:rewrite-test:latest.release")
}

nexusPublishing {
    repositories.getByName("sonatype") {
        nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
        snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
    }
}
