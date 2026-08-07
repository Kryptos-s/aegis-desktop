import com.google.protobuf.gradle.id

plugins {
    `java-library`
    `java-test-fixtures`
    alias(libs.plugins.protobuf)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    // Nullness annotations only; no runtime code.
    api(libs.jspecify)

    // Vault serialization. Aegis vault files are JSON.
    api(libs.json)

    // scrypt is vendored (crypto/bc), but importers need Argon2, PBKDF2 and ASN.1.
    implementation(libs.bouncycastle)

    // otpauth-migration:// payloads (Google Authenticator export).
    implementation(libs.protobuf.javalite)

    // QR code generation (transfer/export) and decoding (image import).
    api(libs.zxing.core)

    // Password-protected zip archives (Authenticator Plus export).
    implementation(libs.zip4j)

    // Third-party authenticators that ship SQLite databases.
    implementation(libs.sqlite.jdbc)

    // CSV exports (Bitwarden).
    implementation(libs.sfm.csv)

    testImplementation(libs.junit)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}

tasks.test {
    maxHeapSize = "2g"
    // scrypt with N=2^15 is deliberately slow; a few vault tests exercise it for real.
    systemProperty("java.awt.headless", "true")
}
