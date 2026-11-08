ARG SERVICE

FROM eclipse-temurin:21.0.13_9-jdk-alpine AS build
ARG SERVICE
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/ common/
COPY vehicles-service/pom.xml vehicles-service/
COPY customers-service/pom.xml customers-service/
COPY sales-service/pom.xml sales-service/
COPY payment-gateway-mock/pom.xml payment-gateway-mock/
COPY ${SERVICE}/src/ ${SERVICE}/src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl ${SERVICE} -am package -DskipTests \
    && cp ${SERVICE}/target/${SERVICE}-*.jar /app.jar \
    && java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
