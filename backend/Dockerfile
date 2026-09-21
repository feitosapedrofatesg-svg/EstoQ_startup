FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B -q
COPY backend/src src
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
RUN adduser -D -h /app estoq
USER estoq
WORKDIR /app
COPY --from=build --chown=estoq /build/target/estoq-startup.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
