plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "io.moderne"
description = "JSON-RPC 2.0 client and server library"

dependencies {
    // https://msgpack.org/
    implementation("org.msgpack:jackson-dataformat-msgpack:latest.release")

    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:latest.release")
}
