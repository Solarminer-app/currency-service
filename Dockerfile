FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src src
RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=builder /workspace/build/libs/currency-rates.jar /app/currency-rates.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/currency-rates.jar"]
