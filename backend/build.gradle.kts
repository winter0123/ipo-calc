plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
    id("checkstyle")
    id("jacoco")
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.vibeaiteam"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    // Lombok for reducing boilerplate (optional but recommended)
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // test 프로필을 쓰지 않는 테스트도 기동 시 외부 동기화(공공 API)를 하지 않게 막는다.
    // direnv의 DATA_GO_KR_SERVICE_KEY로 실제 휴장일이 들어와 휴장일 테스트가 실행 순서에 따라 깨졌다.
    systemProperty("app.holiday-sync.enabled", "false")
    systemProperty("app.ipo-sync.enabled", "false")
    // 앱 시계(ClockConfig)가 Asia/Seoul이라 테스트 JVM도 맞춘다. CI(UTC)에서 한국 자정 전후로 "오늘"이 하루 어긋나 깨지던 문제.
    systemProperty("user.timezone", "Asia/Seoul")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

checkstyle {
    toolVersion = "10.19.0"
    // 경고(google_checks의 기본 심각도)도 0건이어야 한다 — 위반이 쌓이지 않게 빌드에서 막는다.
    maxWarnings = 0
    configFile = file("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "org.checkstyle.google.suppressionfilter.config" to
            file("config/checkstyle/checkstyle-suppressions.xml").absolutePath
    )
}

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        googleJavaFormat("1.22.0")
        importOrder()
        removeUnusedImports()
    }
}
