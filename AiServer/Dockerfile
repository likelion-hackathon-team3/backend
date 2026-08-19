# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install native dependencies if needed
RUN apk add --no-cache libstdc++

COPY --from=build /workspace/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
