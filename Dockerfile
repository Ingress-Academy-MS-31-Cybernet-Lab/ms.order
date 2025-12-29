#FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
#
#WORKDIR /src
#
#COPY build.gradle .
#RUN mvn dependency:go-offline
#COPY src ./src
#
## build
#RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

#COPY --from=builder /src/target/*.jar app.jar

COPY /build/libs/ms.order.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker
ENV DB_HOST=postgres
ENV RABBIT_HOST=rabbitmq

ENTRYPOINT ["java", "-jar", "app.jar"]