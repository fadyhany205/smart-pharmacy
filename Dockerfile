# Container image for Render (or any Docker host). Runs the executable WAR with the "prod" profile.
# Settings come from environment variables: DB_URL, DB_USER, DB_PASS, PORT (see render.yaml).

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
# mysql-client provides mysqldump for the Backup page
RUN apt-get update \
    && apt-get install -y --no-install-recommends mysql-client fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/pharmacy-system.war app.war
# Writable data folder: Hugging Face Spaces runs the container as uid 1000, not root
RUN mkdir -p /app/data/uploads /app/data/logs && chown -R 1000:1000 /app/data
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Africa/Cairo \
    APP_UPLOADDIR=/app/data/uploads \
    LOGGING_FILE_NAME=/app/data/logs/store.log \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
USER 1000
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.war"]
