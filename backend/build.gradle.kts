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

    // authentication-service(U5): SecurityFilterChain(認証の要否の規則・セキュリティヘッダー・ステートレス・CSRF無効)と、
    // 自前の認証フィルタ(BearerAuthenticationFilter)の土台(code-generation-plan.md 前提事項2)。
    // OAuth2 Resource Serverは用いない(署名方式のHS256限定・時計のずれ0・subとSessionのuserIdの照合を自前で制御するため)。
    implementation("org.springframework.boot:spring-boot-starter-security")
    // authentication-service(U5): JWT(HS256)の署名・検証。Spring Boot BOM・Spring Security BOMの管理対象外のため、
    // 最新の安定版を固定する(Spring Security 7.1.1のoauth2-joseが取り込む10.9.1より新しい安定版)。
    implementation("com.nimbusds:nimbus-jose-jwt:10.10")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4.0でDataJpaTest等のテストスライスは個別モジュールへ分離された。
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    // schema-introspector(U2): @WebMvcTest(SchemaIntrospectionControllerTest)用
    // (Jacksonのテスト自動構成を含むstarterでなければObjectMapperがテストスライスへ自動構成されない)。
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // authentication-service(U5): Spring Securityを導入したことによる@WebMvcTest・@SpringBootTestの認証・CSRFの扱い
    // (SecurityMockMvcRequestPostProcessors等)のテスト支援。
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// config-import-export(U9): エクスポートの`appVersion`(書き出したアプリケーションのバージョン)を、
// ビルド情報(META-INF/build-info.properties。`BuildProperties`Bean)から得る(code-generation-plan.md Step 16)。
springBoot {
    buildInfo()
}

tasks.test {
    // config-import-export(U9、code-generation-plan.md 前提事項7): 性能の確認(NFR1.1・NFR1.2。JUnitのタグ`nfr-performance`)は、
    // 通常のtestタスクから除外する。専用のnfrPerformanceTestタスクで実行する(Build and Testで実行する)。
    useJUnitPlatform {
        excludeTags("nfr-performance")
    }
    finalizedBy(tasks.jacocoTestReport)
}

// 性能の確認(NFR1.1・NFR1.2): `./gradlew :backend:nfrPerformanceTest --tests "com.mastersmith.configio.performance.*"`。
// team.mdの「負荷・性能テストは既定には含めない」に従い、負荷の掛け方は含めず、単一の利用者による繰り返しの実行だけを行う。
val nfrPerformanceTest by tasks.registering(Test::class) {
    description = "Runs the NFR performance checks (JUnit tag nfr-performance) of config-import-export."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("nfr-performance")
    }
    testLogging {
        showStandardStreams = true
    }
    // 想定規模の上限の設定(本体は数MB)を、繰り返し取り込むため、既定より大きなヒープを与える。
    maxHeapSize = "1g"
    // 計測は、他のテストと同時に走らせない。
    shouldRunAfter(tasks.test)
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
