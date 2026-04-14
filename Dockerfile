# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 1) Copy Maven wrapper + pom — these change rarely
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 2) Resolve all dependencies (cached until pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# 3) Now copy source and build
COPY src src
RUN ./mvnw package -DskipTests -o

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
