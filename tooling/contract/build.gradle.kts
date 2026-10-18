plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:protocol"))
    implementation(project(":tooling:codegen"))
    implementation(project(":tooling:frontend-patch"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
