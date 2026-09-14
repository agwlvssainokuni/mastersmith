// Copyright 2026 agwlvssainokuni
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    checkstyle
    jacoco
    id("com.diffplug.spotless") version "8.10.2"
}

group = "com.mastersmith"
version = "0.0.1-SNAPSHOT"
description = "MasterSmith backend (Java 25 + Spring Boot). config-engine (U1) and future units."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring BootのBOMをGradleのplatform機構で取り込む(io.spring.dependency-managementは不使用)。
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // data-import-export(U8): CSVエクスポート/インポートのストリーミング読み書き
    // (nfr-requirements/tech-stack-decisions.md「CSVパースライブラリ: Apache Commons CSV」)。
    implementation("org.apache.commons:commons-csv:1.12.0")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4.0でDataJpaTest等のテストスライスは個別モジュールへ分離された。
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// team.md確定のmvp系スコープフロア: 行カバレッジ80%以上。
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

spotless {
    java {
        target("src/**/*.java")
        licenseHeaderFile(file("config/spotless/license-header.java"))
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
