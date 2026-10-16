FROM eclipse-temurin:21-jre

WORKDIR /app

COPY deploy/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]