# Build and runtime use the same Java major version as the project target
# (java.version in pom.xml) and the CI unit-test job: 27. Temurin 27 builds of
# the Maven image start at Maven 3.10.
FROM maven:3.10-eclipse-temurin-27 AS build
WORKDIR /app
# Dependencies first: this layer is only rebuilt when pom.xml changes, so a
# change to Java code reuses it instead of downloading everything again.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# JRE only, on Alpine: no compiler, no Maven, no Ubuntu userland in the image
# that actually runs.
FROM eclipse-temurin:27-jre-alpine
WORKDIR /app
# Run as an unprivileged user. The numeric id lets Kubernetes verify
# runAsNonRoot; the jar stays owned by root, so the app can read it but not
# modify it.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=build /app/target/student-news-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
