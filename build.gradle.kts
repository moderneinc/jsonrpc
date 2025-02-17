import io.github.gradlenexus.publishplugin.NexusPublishExtension

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "io.moderne"
description = "JSON-RPC 2.0 client and server library"

dependencies {
    // https://msgpack.org/
    implementation("org.msgpack:jackson-dataformat-msgpack:latest.release")

    implementation("io.micrometer:micrometer-core:latest.release")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:latest.release")

    implementation("org.openrewrite:rewrite-properties:latest.release")
    implementation("org.openrewrite:rewrite-json:latest.release")

    testImplementation("org.openrewrite:rewrite-test:latest.release")
}

configure<NexusPublishExtension>() {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
