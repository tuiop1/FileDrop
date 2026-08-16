# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

COPY --from=build /workspace/target/FileDrop-*.jar /app/filedrop.jar

ENV SPRING_PROFILES_ACTIVE=prod

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/filedrop.jar"]
