plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.kigtts.engines.cloudasr"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["main"].java.setSrcDirs(listOf("src/main/kotlin/com/kigtts/engines/cloudasr"))
    sourceSets["test"].java.setSrcDirs(listOf("src/test/kotlin/com/kigtts/engines/cloudasr"))
}
kotlin.sourceSets.getByName("main").kotlin.setSrcDirs(listOf("src/main/kotlin/com/kigtts/engines/cloudasr"))
kotlin.sourceSets.getByName("test").kotlin.setSrcDirs(listOf("src/test/kotlin/com/kigtts/engines/cloudasr"))
tasks.register<Sync>("collectPluginDependencies") {
    from(configurations.getByName("debugRuntimeClasspath"))
    into(layout.buildDirectory.dir("plugin-dependencies"))
}
dependencies {
    compileOnly(project(":plugin-android-api"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(project(":plugin-android-api"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
