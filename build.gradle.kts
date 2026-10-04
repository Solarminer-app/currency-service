import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

description = "SolarMiner Currency Service"

base {
    archivesName.set(providers.gradleProperty("artifact"))
}

springBoot {
    buildInfo()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("currency-rates.jar")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    val dockerImage = providers.gradleProperty("dockerImage")

    imageName.set(dockerImage.map { image -> "$image:${project.version}" })
    environment.put("BP_NATIVE_IMAGE", "true")
    environment.put(
        "BP_NATIVE_IMAGE_BUILD_ARGUMENTS",
        "-march=compatibility --initialize-at-run-time=sun.security.util.Password,sun.security.util.Password\$ConsoleHolder"
    )

    docker {
        publishRegistry {
            username = System.getenv("DOCKER_USER")
            password = System.getenv("DOCKER_PASS")
        }
    }
}

tasks.register("printVersion") {
    group = "versioning"
    description = "Prints the service version."
    doLast { println(project.version) }
}

tasks.register("printDockerImage") {
    group = "versioning"
    description = "Prints the Docker image repository."
    doLast { println(providers.gradleProperty("dockerImage").get()) }
}

tasks.register("printArtifactName") {
    group = "versioning"
    description = "Prints the artifact base name."
    doLast { println(providers.gradleProperty("artifact").get()) }
}
