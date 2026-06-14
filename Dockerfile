# ============================================================
# Build stage
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom first (for Docker layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ============================================================
# Runtime stage
# ============================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Create upload directory
RUN mkdir -p /app/storage/uploads

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
