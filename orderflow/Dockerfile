FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --uid 10001 --create-home spring
COPY --from=build --chown=spring:spring /workspace/target/orderflow-0.0.1-SNAPSHOT.jar /app/orderflow.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Duser.timezone=UTC", "-jar", "/app/orderflow.jar"]
