# --- Stage 1: Build ---
FROM maven:3.10-eclipse-temurin-27 AS build
WORKDIR /app

# Copy pom.xml and download dependencies first (caching layer)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build the JAR
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime ---
FROM eclipse-temurin:27-jre-alpine
WORKDIR /app

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create the upload directory and give ownership to the appuser
RUN mkdir -p /data/avatars && chown -R appuser:appgroup /data/avatars

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Switch to the non-root user
USER appuser

# Expose the default port
EXPOSE 8090

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]