plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("java")
    id("checkstyle")
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.apache.commons:commons-csv:1.14.1")
    
    // Lombok for reducing boilerplate (optional but recommended)
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // test 프로필을 쓰지 않는 테스트도 기동 시 외부 동기화(공공 API)를 하지 않게 막는다.
    // direnv의 DATA_GO_KR_SERVICE_KEY로 실제 휴장일이 들어와 휴장일 테스트가 실행 순서에 따라 깨졌다.
    systemProperty("app.holiday-sync.enabled", "false")
    systemProperty("app.ipo-sync.enabled", "false")
}

checkstyle {
    toolVersion = "10.19.0"
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
