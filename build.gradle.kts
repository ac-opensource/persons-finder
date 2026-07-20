import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("jvm") version "2.4.10"
	kotlin("plugin.spring") version "2.4.10"
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
	implementation("org.webjars.npm:leaflet:1.9.4")
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

val testSourceSet = sourceSets["test"]

tasks.register<Test>("liveAiSmoke") {
	group = "verification"
	description = "Runs exactly one selected provider's explicit three-call live AI smoke"
	testClassesDirs = testSourceSet.output.classesDirs
	classpath = testSourceSet.runtimeClasspath
	filter {
		includeTestsMatching(
			"com.persons.finder.person.bio.remote.RemoteBioGeneratorLiveTest",
		)
	}
	systemProperty("liveAiSmoke.required", "true")
	systemProperty(
		"liveAiSmoke.reportDir",
		layout.buildDirectory.dir("reports/live-ai-smoke").get().asFile.absolutePath,
	)
	systemProperty(
		"liveAiSmoke.durableReportDir",
		layout.projectDirectory.dir(".agents/evidence/live-ai-smoke").asFile.absolutePath,
	)
	outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("liveAiEval") {
	group = "verification"
	description = "Runs the explicit, billable live AI reliability evaluation"
	dependsOn(tasks.named("testClasses"))
	classpath = testSourceSet.runtimeClasspath
	mainClass.set("com.persons.finder.person.bio.remote.eval.LiveBioEvalMain")
	workingDir = layout.projectDirectory.asFile
	systemProperty(
		"liveAiEval.reportDir",
		layout.buildDirectory.dir("reports/live-ai-eval").get().asFile.absolutePath,
	)
	systemProperty(
		"liveAiEval.durableReportDir",
		layout.projectDirectory.dir(".agents/evidence/live-ai-eval").asFile.absolutePath,
	)
	outputs.upToDateWhen { false }
}
