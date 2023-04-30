@file:Suppress(
    "unused",
    "DSL_SCOPE_VIOLATION",
    "UNUSED_VARIABLE",
    "PropertyName",
)
@file:OptIn(
    org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class
)

import com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJsOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import java.net.URI

plugins {
    alias(libs.plugins.testlogger)
    alias(libs.plugins.versionCheck)
    alias(libs.plugins.doctor)
    alias(libs.plugins.dokka)
    alias(libs.plugins.sonar)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.kotlinx.apiValidator)

    `project-report`
    `maven-publish`
    distribution
    signing
}

val defaultJavaToolchain: Int = 11
val jvmTargetMinimum: String by properties
val kotlinLanguage: String by properties
val lockDeps: String by properties
val nodeVersion: String by properties
val sonarScan: String by properties
val GROUP: String by properties
val VERSION: String by properties

group = GROUP
version = VERSION

val kotlinCompilerArgs = listOf(
    "-progressive",
    "-Xcontext-receivers",
)

val jvmFlags = kotlinCompilerArgs.plus(listOf(
    "-Xjvm-default=all",
    "-Xjsr305=strict",
    "-Xallow-unstable-dependencies",
    "-Xemit-jvm-type-annotations",
))

val cacheDisabledTasks = listOf(
    "compileNix64MainKotlinMetadata",
    "jvmTest",
    "compileTestKotlinLinuxX64",
    "linkDebugTestLinuxX64",
    "koverXmlReport",
)

val isReleaseBuild = !VERSION.contains("SNAPSHOT")

repositories {
    maven("https://maven.pkg.st/")
}

testlogger {
    theme = MOCHA_PARALLEL
    showExceptions = System.getenv("TEST_EXCEPTIONS") == "true"
    showFailed = true
    showPassed = true
    showSkipped = true
    showFailedStandardStreams = true
    showFullStackTraces = true
    slowThreshold = 30000L
}

kotlin {
    explicitApi()

    targets {
        js(IR) {
            compilations.all {
                kotlinOptions {
                    sourceMap = true
                    moduleKind = "umd"
                    metaInfo = true
                }
            }
            browser()
            nodejs()
        }

        wasm {
            browser()
        }

        jvm {
            jvmToolchain(jvmTargetMinimum.toIntOrNull() ?: defaultJavaToolchain)
        }

        if (HostManager.hostIsMac) {
            macosX64()
            macosArm64()
            iosX64()
            iosArm64()
            iosSimulatorArm64()
            watchosArm32()
            watchosArm64()
            watchosX64()
            watchosSimulatorArm64()
            tvosArm64()
            tvosX64()
            tvosSimulatorArm64()
        }
        if (HostManager.hostIsMingw || HostManager.hostIsMac) {
            mingwX64 {
                binaries.findTest(DEBUG)!!.linkerOpts = mutableListOf("-Wl,--subsystem,windows")
            }
        }
        if (HostManager.hostIsLinux || HostManager.hostIsMac) {
            linuxX64()
            linuxArm64()
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val nonWasmMain by creating { dependsOn(commonMain) }
        val nonWasmTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val nonJvmMain by creating {
            dependsOn(nonWasmMain)
        }
        val nonJvmTest by creating {
            dependsOn(nonWasmTest)
        }
        val jvmMain by getting { dependsOn(nonWasmMain) }
        val jvmTest by getting { dependsOn(nonWasmTest) }
        val jsMain by getting { dependsOn(nonJvmMain) }
        val jsTest by getting { dependsOn(nonJvmTest) }
        val nativeMain by creating {
            dependsOn(nonJvmMain)
            dependsOn(nonWasmMain)
        }
        val nativeTest by creating {
            dependsOn(nonJvmTest)
            dependsOn(nonWasmTest)
        }
        val wasmMain by getting {
            dependsOn(commonMain)
        }
        val wasmTest by getting {
            dependsOn(commonTest)
        }
        val nix64Main by creating { dependsOn(nativeMain) }
        val nix64Test by creating { dependsOn(nativeTest) }
        val nix32Main by creating { dependsOn(nativeMain) }
        val nix32Test by creating { dependsOn(nativeTest) }

        if (HostManager.hostIsMac) {
            val appleMain by creating { dependsOn(nativeMain) }
            val appleTest by creating { dependsOn(nativeTest) }
            val apple64Main by creating {
                dependsOn(appleMain)
                dependsOn(nix64Main)
            }
            val apple64Test by creating {
                dependsOn(appleTest)
                dependsOn(nix64Test)
            }
            val apple32Main by creating {
                dependsOn(appleMain)
                dependsOn(nix32Main)
            }
            val apple32Test by creating {
                dependsOn(appleTest)
                dependsOn(nix32Test)
            }
            val iosX64Main by getting { dependsOn(apple64Main) }
            val iosX64Test by getting { dependsOn(apple64Test) }
            val iosArm64Main by getting { dependsOn(apple64Main) }
            val iosArm64Test by getting { dependsOn(apple64Test) }
            val macosX64Main by getting { dependsOn(apple64Main) }
            val macosX64Test by getting { dependsOn(apple64Test) }
            val macosArm64Main by getting { dependsOn(apple64Main) }
            val macosArm64Test by getting { dependsOn(apple64Test) }
            val iosSimulatorArm64Main by getting { dependsOn(apple64Main) }
            val iosSimulatorArm64Test by getting { dependsOn(apple64Test) }
            val watchosArm32Main by getting { dependsOn(apple32Main) }
            val watchosArm32Test by getting { dependsOn(apple32Test) }
            val watchosArm64Main by getting { dependsOn(apple64Main) }
            val watchosArm64Test by getting { dependsOn(apple64Test) }
            val watchosX64Main by getting { dependsOn(apple64Main) }
            val watchosX64Test by getting { dependsOn(apple64Test) }
            val watchosSimulatorArm64Main by getting { dependsOn(apple64Main) }
            val watchosSimulatorArm64Test by getting { dependsOn(apple64Test) }
            val tvosArm64Main by getting { dependsOn(apple64Main) }
            val tvosArm64Test by getting { dependsOn(apple64Test) }
            val tvosX64Main by getting { dependsOn(apple64Main) }
            val tvosX64Test by getting { dependsOn(apple64Test) }
            val tvosSimulatorArm64Main by getting { dependsOn(apple64Main) }
            val tvosSimulatorArm64Test by getting { dependsOn(apple64Test) }
        }

        if (HostManager.hostIsMingw || HostManager.hostIsMac) {
            val mingwMain by creating { dependsOn(nativeMain) }
            val mingwTest by creating { dependsOn(nativeTest) }
            val mingwX64Main by getting { dependsOn(mingwMain) }
            val mingwX64Test by getting { dependsOn(mingwTest) }
        }

        if (HostManager.hostIsLinux || HostManager.hostIsMac) {
            val linuxX64Main by getting { dependsOn(nix64Main) }
            val linuxX64Test by getting { dependsOn(nix64Test) }
            val linuxArm64Main by getting { dependsOn(nix64Main) }
            val linuxArm64Test by getting { dependsOn(nix64Test) }
        }
    }

    sourceSets.all {
        languageSettings.apply {
            apiVersion = kotlinLanguage
            languageVersion = kotlinLanguage
            progressiveMode = true
            optIn("kotlin.ExperimentalUnsignedTypes")
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                apiVersion = kotlinLanguage
                languageVersion = kotlinLanguage
                allWarningsAsErrors = true
                when (this) {
                    is KotlinJvmOptions -> {
                        jvmTarget = jvmTargetMinimum
                        javaParameters = true
                        freeCompilerArgs = jvmFlags
                    }
                    is KotlinJsOptions -> {
                        sourceMap = true
                        moduleKind = "umd"
                        metaInfo = true
                    }
                    else -> {
                        freeCompilerArgs = kotlinCompilerArgs
                    }
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 4
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = jvmTargetMinimum
    targetCompatibility = jvmTargetMinimum
    options.isFork = true
    options.isIncremental = true
}

rootProject.plugins.withType(NodeJsRootPlugin::class.java) {
    // 16+ required for Apple Silicon support
    // https://youtrack.jetbrains.com/issue/KT-49109#focus=Comments-27-5259190.0-0
    rootProject.the<NodeJsRootExtension>().download = true
    rootProject.the<NodeJsRootExtension>().nodeVersion = nodeVersion
    rootProject.the<NodeJsRootExtension>().nodeDownloadBaseUrl = "https://node.pkg.st/"
}
rootProject.plugins.withType(YarnPlugin::class.java) {
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport = YarnLockMismatchReport.WARNING
    rootProject.the<YarnRootExtension>().reportNewYarnLock = false
    rootProject.the<YarnRootExtension>().yarnLockAutoReplace = true
}

val ktlintConfig: Configuration by configurations.creating

dependencies {
    ktlintConfig(libs.ktlint)
}

detekt {
    parallel = true
    ignoreFailures = true
    config = rootProject.files(".github/detekt.yml")
}

val ktlint by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Check Kotlin code style."
    classpath = ktlintConfig
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("src/**/*.kt")
}

val ktlintformat by tasks.registering(JavaExec::class) {
    group = "formatting"
    description = "Fix Kotlin code style deviations."
    classpath = ktlintConfig
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F", "src/**/*.kt", "*.kts")
}

val checkTask: TaskProvider<Task> = tasks.named("check")

checkTask.configure {
    dependsOn(ktlint)
}

// Generate PROJECT_DIR_ROOT for referencing local mocks in tests

val projectDirGenRoot = "$buildDir/generated/projectdir/kotlin"
val projectDirPath: String = projectDir.absolutePath
val generateProjDirValTask: TaskProvider<Task> = tasks.register("generateProjectDirectoryVal") {
    mkdir(projectDirGenRoot)
    val projDirFile = File("$projectDirGenRoot/projdir.kt")
    projDirFile.writeText("")
    projDirFile.appendText(
        """
            |package dev.elide.uuid
            |
            |internal const val PROJECT_DIR_ROOT = ""${'"'}${projectDirPath}""${'"'}
            |
        """.trimMargin()
    )
}

kotlin.sourceSets.named("commonTest") {
    this.kotlin.srcDir(projectDirGenRoot)
}

// Ensure this runs before any test compile task
tasks.withType<AbstractCompile>().configureEach {
    if (name.lowercase().contains("test")) {
        dependsOn(generateProjDirValTask)
    }
}

tasks.withType<AbstractKotlinCompileTool<*>>().configureEach {
    if (name.lowercase().contains("test")) {
        dependsOn(generateProjDirValTask)
    }
}

tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.withType<Zip>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

configurations.all {
    resolutionStrategy {
        // fail eagerly on version conflict (includes transitive dependencies)
        failOnVersionConflict()

        // prefer modules that are part of this build
        preferProjectModules()

        if (name.contains("detached")) {
            disableDependencyVerification()
        }
    }
}

listOf(
    "jsTest",
    "compileTestDevelopmentExecutableKotlinJs",
).forEach { taskName ->
    tasks.named(taskName) {
        enabled = false  // disabled because it breaks on macOS
    }
}

sonarqube {
    properties {
        listOf(
            "sonar.projectKey" to "elide-dev_uuid",
            "sonar.organization" to "elide-dev",
            "sonar.host.url" to "https://sonarcloud.io",
            "sonar.coverage.jacoco.xmlReportPaths" to "${project.rootDir}/build/reports/kover/xml/report.xml",
        ).forEach { (key, value) ->
            property(key, value)
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.isIncremental = true
}

plugins.withType(io.gitlab.arturbosch.detekt.DetektPlugin::class) {
    tasks.withType(io.gitlab.arturbosch.detekt.Detekt::class) detekt@{
        reports.sarif.required.set(true)
        reports.sarif.outputLocation.set(
            rootProject.buildDir.resolve("reports/detekt/report.sarif")
        )
    }
}

//tasks.withType<DokkaTask> {
//    dokkaSourceSets {
//        named("commonMain") {
//            samples.from("src/commonTest/kotlin")
//        }
//    }
//}

if (lockDeps == "true") {
    dependencyLocking {
        lockAllConfigurations()
    }
}

val resolveAndLockAll: TaskProvider<Task> = tasks.register("resolveAndLockAll") {
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks)
    }
    doLast {
        configurations.filter {
            // Add any custom filtering on the configurations to be resolved
            it.isCanBeResolved
        }.forEach { it.resolve() }
    }
}

tasks.register("relock") {
    dependsOn(
        tasks.dependencies,
        resolveAndLockAll,
    )
}

val dokkaHtml by tasks.getting(DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

val mavenUsername: String? = properties["mavenUsername"] as? String
val mavenPassword: String? = properties["mavenPassword"] as? String

signing {
    isRequired = isReleaseBuild
    sign(configurations.archives.get())
    sign(publishing.publications)
}

tasks.withType(Sign::class) {
    onlyIf {
        isReleaseBuild
    }
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)
        artifactId = artifactId.replace("uuid", "elide-uuid")

        pom {
            name.set("Elide UUID")
            url.set("https://elide.dev")
            description.set("UUID tools for Kotlin Multiplatform.")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://github.com/elide-dev/elide/blob/v3/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("sgammon")
                    name.set("Sam Gammon")
                    email.set("samuel.gammon@gmail.com")
                }
            }
            scm {
                url.set("https://github.com/elide-dev/elide")
            }
        }
    }

    repositories {
        maven {
            url = URI.create(when {
                // if we are given an explicit repository, use it
                !(properties["REPOSITORY"] as? String).isNullOrBlank() -> (properties["REPOSITORY"] as String)

                // otherwise, default to releases for a release build, or fall back to the elide snapshots repo
                else -> if (isReleaseBuild) {
                    "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                } else {
                    "gcs://elide-snapshots/repository/v3"
                }
            })
            if (!mavenUsername.isNullOrBlank() && !mavenPassword.isNullOrBlank()) {
                credentials {
                    username = mavenUsername
                    password = mavenPassword
                }
            }
        }
    }
}

val reports: TaskProvider<Task> = tasks.register("reports") {
    dependsOn(
        tasks.koverXmlReport,
        tasks.dependencyReport,
        tasks.htmlDependencyReport,
    )
}

val allTests: TaskProvider<Task> = tasks.named("allTests")
val test: Task = tasks.create("test") {
    dependsOn(
        allTests,
    )
}

val check: TaskProvider<Task> = tasks.named("check") {
    dependsOn(
        test,
        ktlint,
        tasks.apiCheck,
        tasks.koverVerify,
    )
}

tasks.create("preMerge") {
    dependsOn(
        tasks.build,
        tasks.check,
        reports,
    )
    if (sonarScan == "true") {
        dependsOn(
            tasks.sonar,
        )
    }
}

afterEvaluate {
    listOf(
        "wasmTest",
        "compileTestDevelopmentExecutableKotlinWasm",
    ).forEach {
        tasks.named(it).configure {
            enabled = false
        }
    }

    cacheDisabledTasks.forEach {
        try {
            tasks.named(it).configure {
                doNotTrackState("too big for build cache")
            }
        } catch (err: Throwable) {
            // ignore
        }
    }
}

val publishMac by tasks.registering {
    dependsOn(
        "publishIosArm64PublicationToMavenRepository",
        "publishIosSimulatorArm64PublicationToMavenRepository",
        "publishIosX64PublicationToMavenRepository",
        "publishTvosArm64PublicationToMavenRepository",
        "publishTvosSimulatorArm64PublicationToMavenRepository",
        "publishTvosX64PublicationToMavenRepository",
        "publishWatchosArm32PublicationToMavenRepository",
        "publishWatchosArm64PublicationToMavenRepository",
        "publishWatchosSimulatorArm64PublicationToMavenRepository",
        "publishWatchosX64PublicationToMavenRepository",
        "publishMacosArm64PublicationToMavenRepository",
        "publishMacosX64PublicationToMavenRepository",
        "publishJvmPublicationToMavenRepository",
        "publishJsPublicationToMavenRepository",
        "publishWasmPublicationToMavenRepository",
        "publishKotlinMultiplatformPublicationToMavenRepository",
    )
}

val publishWindows by tasks.registering {
    dependsOn(
        "publishMingwX64PublicationToMavenRepository",
    )
}

val publishLinux by tasks.registering {
    dependsOn(
        "publishLinuxX64PublicationToMavenRepository",
        "publishLinuxArm64PublicationToMavenRepository",
    )
}
