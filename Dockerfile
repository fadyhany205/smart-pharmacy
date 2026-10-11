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
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Africa/Cairo \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.war"]
