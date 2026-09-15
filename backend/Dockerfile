FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml backend/
RUN mvn -pl backend dependency:go-offline -B -q
COPY backend/src backend/src
RUN mvn -pl backend clean package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
RUN adduser -D -h /app estoq
USER estoq
WORKDIR /app
COPY --from=build --chown=estoq /build/backend/target/estoq-startup.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
