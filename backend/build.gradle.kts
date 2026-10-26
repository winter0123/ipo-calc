plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
    id("checkstyle")
    id("jacoco")
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.ipocalc"
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
    // JPA 파생 메서드로 표현하기 어려운 조인·집계 조건은 QueryDSL로 구성한다.
    implementation("io.github.openfeign.querydsl:querydsl-jpa:6.10.1")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    // 웹 푸시(VAPID + 메시지 암호화). 최신 5.1.2(2025-02), 암호화는 BouncyCastle 사용
    implementation("nl.martijndwars:web-push:5.1.2") {
        // 구형 jdk15on 대신 유지보수되는 jdk18on을 쓴다(패키지명 같음)
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    // preparePost 시그니처가 쓰는 타입(web-push는 런타임 의존으로만 선언). jose4j는 구버전 0.7.9 대신 최신으로 올린다
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.bitbucket.b_c:jose4j:0.9.6")
    
    // Lombok for reducing boilerplate (optional but recommended)
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:6.10.1:jpa")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

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
    systemProperty("app.open-price-sync.enabled", "false")
    systemProperty("app.notification.enabled", "false")
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
