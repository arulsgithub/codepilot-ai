# syntax=docker/dockerfile:1

# ──────────────────────────────────────────────────────────────────────────────
# Stage 1: build the Angular app
# ──────────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS frontend

WORKDIR /frontend

# package files first, on their own layer: dependencies change far less often than
# source, so an edit to a component reuses the cached npm install instead of
# re-downloading node_modules on every build.
COPY frontend/package.json frontend/package-lock.json ./
# `npm ci` not `npm install` - installs exactly the lockfile, fails if they disagree,
# and never silently upgrades a dependency during a deploy.
RUN npm ci

COPY frontend/ ./
RUN npm run build -- --configuration production

# ──────────────────────────────────────────────────────────────────────────────
# Stage 2: build the Spring Boot jar, with the Angular output baked in
# ──────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS backend

WORKDIR /backend

# Maven settings BEFORE any mvn invocation, or the mirror is not in effect for the
# very download that needs it most. Points Maven Central at Google's mirror: CI
# builders share outbound IPs, and repo.maven.apache.org rate-limits by IP, so a
# cold build pulling hundreds of artifacts gets a 429 partway through.
COPY backend/.mvn/settings.xml /root/.m2/settings.xml

# Same caching trick as the frontend: resolve dependencies from the pom alone, so a
# Java edit does not re-download the entire Maven repository.
COPY backend/pom.xml ./

# Retry transient HTTP failures. Maven's default retry count is 0, which is far too
# brittle for CI - one dropped connection among several hundred artifacts should not
# cost a ten-minute rebuild.
RUN mvn -B \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    dependency:go-offline

COPY backend/src ./src

# The Angular build lands in src/main/resources/static/, so Spring serves it from
# the classpath at runtime - one origin, no CORS, no second hosting service.
#
# Angular 17+ with the `application` builder emits into dist/<project>/browser/,
# NOT dist/<project>/. Copying the wrong level gives you a jar whose index.html is
# one directory too deep and a blank page with no error.
COPY --from=frontend /frontend/dist/codepilot-ai-frontend/browser/ ./src/main/resources/static/

RUN mvn -B \
    -Dmaven.wagon.http.retryHandler.count=5 \
    clean package -DskipTests

# ──────────────────────────────────────────────────────────────────────────────
# Stage 3: runtime
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Non-root. If anything ever escapes the JVM, it should not own the container.
RUN addgroup -S codepilot && adduser -S codepilot -G codepilot

WORKDIR /app

COPY --from=backend /backend/target/*.jar app.jar

# Where cloned repositories go. On a platform with an ephemeral filesystem this is
# wiped on every restart - RepositoryRegistryService.resolvePath re-clones on demand,
# which is why that exists.
RUN mkdir -p /app/codepilot-workspace && chown -R codepilot:codepilot /app

USER codepilot

EXPOSE 8080

# -XX:MaxRAMPercentage, not -Xmx: the JVM must size its heap from the CONTAINER's
# memory limit, not the host's. Without it, a JVM in a 512MB container can believe
# it has the whole machine's RAM, size the heap accordingly, and get OOM-killed by
# the kernel with no Java-level error - just a container that vanishes.
#
# 70% leaves room for metaspace, thread stacks, and the off-heap buffers Netty uses
# for the embedding and LLM clients.
#
# UseSerialGC on purpose: on a single small container the parallel collectors spend
# more on coordination than they save, and reserve more memory per thread.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

# `exec` matters: without it, sh stays as PID 1 and swallows the SIGTERM the platform
# sends on shutdown, so the JVM is killed rather than shutting down cleanly and
# waitForTasksToCompleteOnShutdown never runs.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]