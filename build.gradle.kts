import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
}

group = "com.persons.finder"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<KotlinCompile>().configureEach {
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xjsr305=strict",
			"-Xannotation-default-target=param-property",
		)
		jvmTarget = JvmTarget.JVM_17
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	doFirst {
		val colimaSocket =
			file("${System.getProperty("user.home")}/.colima/default/docker.sock")
		if (System.getenv("DOCKER_HOST") == null && colimaSocket.exists()) {
			environment("DOCKER_HOST", "unix://$colimaSocket")
			environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
		}
	}
}
