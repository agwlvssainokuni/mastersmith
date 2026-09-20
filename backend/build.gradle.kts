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
    // schema-introspector(U2): 最初にRESTコントローラ(C8)を必要とするUnitのため、ここでWeb starterを導入する。
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // data-import-export(U8): CSVエクスポート/インポートのストリーミング読み書き
    // (nfr-requirements/tech-stack-decisions.md「CSVパースライブラリ: Apache Commons CSV」)。
    implementation("org.apache.commons:commons-csv:1.12.0")
    // permission-engine(U3): 実効権限のインメモリキャッシュ(performance-design.md「キャッシュアーキテクチャ」、Caffeine採用)。
    implementation("com.github.ben-manes.caffeine:caffeine")
    // permission-engine(U3): メトリクス計装(observability-design.md、MeterRegistry Beanの自動構成に必要)。
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // audit-logging(U7)前提修正: スキーマ移行ツール(Flyway)の新規導入
    // (code-generation-plan.md「前提修正: Flywayの導入」、performance-design.md Q2確定)。
    // H2はFlyway Community Edition(flyway-core)が標準サポートするため、
    // 別モジュール(flyway-database-h2、PostgreSQL/Oracle等で必要な形式)は不要
    // (計画時点の想定との差異。Maven Centralに同artifactId自体が存在しないことを確認済み)。
    // Spring Boot 4.xはオートコンフィグレーションをモジュール分割しており、
    // FlywayAutoConfiguration等の自動構成本体はspring-boot-flywayが提供する
    // (flyway-core単体ではSpring Bootが起動時にマイグレーションを自動実行しない)。
    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("com.h2database:h2")

    // user-management(U4): 招待メールのHTML生成に用いる自作mustacheエンジン。Gitサブモジュール
    // (external/java-mustache-processor)を、settings.gradle.ktsのincludeBuild(複合ビルド)で参照する
    // (code-generation-plan.md 前提事項5)。
    implementation("cherry.mustache:cherry-mustache-core")
    // user-management(U4): 招待メールの送信(SMTP、FR2.8)。
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // user-management(U4): Argon2idのパスワードハッシュ(Argon2PasswordEncoder)。バージョンは
    // Spring Boot BOM(spring-security-bomを取り込み)で管理される。Argon2PasswordEncoderは内部で
    // BouncyCastleを用いるため、bcprovを明示的に追加する(BOMの管理対象外のため最新の安定版を固定)。
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4.0でDataJpaTest等のテストスライスは個別モジュールへ分離された。
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    // schema-introspector(U2): @WebMvcTest(SchemaIntrospectionControllerTest)用
    // (Jacksonのテスト自動構成を含むstarterでなければObjectMapperがテストスライスへ自動構成されない)。
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
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
