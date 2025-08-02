FROM openjdk:17-jdk-alpine

RUN apk update && apk add libreoffice

ARG JAR_FILE=build/libs/sample-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
