# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar \
    && cp "$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /workspace/momna-backend.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/momna-backend.jar /app/momna-backend.jar

EXPOSE 8080
ENV PORT=8080 \
    MOMNA_ENV=stage \
    MOMNA_ROLE=api

ENTRYPOINT ["java","-jar","/app/momna-backend.jar"]
