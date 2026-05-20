plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:2.28.8")
    }
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mustache")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-h2console")
    runtimeOnly("com.h2database:h2")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("software.amazon.awssdk:sqs")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:cognitoidentityprovider")
    implementation("software.amazon.awssdk:secretsmanager")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-mustache-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
