plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.1.1")
    implementation(project(":pleo-antaeus-data"))
    compile(project(":pleo-antaeus-models"))
}