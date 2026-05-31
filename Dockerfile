# GoLinkGone backend — runtime image.
#
# Build the fat JAR first on the host:    ./mvnw -DskipTests package
# Then build the image:                   docker build -t glg-backend:latest .
#
# The MaxMind GeoLite2 database is NOT baked into the image. The host must
# provide /opt/glg/GeoLite2-City.mmdb (and ideally a writable /opt/glg so the
# GeoLiteUpdater can refresh it weekly). Mount it at runtime:
#
#   docker run -d --name glg-backend \
#     --restart unless-stopped \
#     -p 127.0.0.1:8080:8080 \
#     -v /opt/glg:/opt/glg \
#     -v /var/log/glg:/var/log/glg \
#     --env-file /opt/glg/glg.env \
#     glg-backend:latest

FROM eclipse-temurin:21-jre-alpine

# Run as a non-root user. Tomcat doesn't need root and a compromised JVM
# shouldn't be able to chown system files.
RUN addgroup -S glg && adduser -S -G glg -h /app glg

WORKDIR /app

# Pre-create the mountpoints with the right ownership so the bind-mounted
# host directories are writable by the unprivileged process.
RUN mkdir -p /opt/glg /var/log/glg && \
    chown -R glg:glg /app /opt/glg /var/log/glg

# Copy the Spring Boot fat JAR. The build context's .dockerignore excludes
# the rest of target/ and the host's local mmdb.
COPY --chown=glg:glg target/IdResolutionSystem-0.0.1-SNAPSHOT.jar app.jar

USER glg
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]