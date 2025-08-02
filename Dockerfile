FROM gradle:8.5.0-jdk17 AS builder
COPY --chown=gradle:gradle . /home/gradle/project
WORKDIR /home/gradle/project
RUN gradle build -x test

FROM openjdk:17-jdk-alpine
COPY --from=builder /home/gradle/project/build/libs/sample-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]