FROM eclipse-temurin:21-jre-alpine

ARG JAR_FILE=target/market-bot-0.0.1-SNAPSHOT.jar

WORKDIR /app

RUN addgroup -S marketbot && adduser -S marketbot -G marketbot

COPY ${JAR_FILE} app.jar

USER marketbot

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
