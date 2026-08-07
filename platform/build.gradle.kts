plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))
    implementation(libs.coroutines.core)

    // Native OS integration: Secret Service (Linux), DPAPI + Credential Manager (Windows),
    // Keychain and LocalAuthentication (macOS).
    implementation(libs.jna)
    implementation(libs.jna.platform)

    implementation(libs.slf4j.api)

    testImplementation(libs.junit)
}
