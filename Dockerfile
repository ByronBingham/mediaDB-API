# syntax=docker/dockerfile:1

FROM maven:3-eclipse-temurin-21 AS jar
ARG ARG_VERSION
ENV VERSION=${ARG_VERSION}
COPY . /bmedia_api
WORKDIR /bmedia_api
RUN mvn package

FROM eclipse-temurin:21
ARG ARG_VERSION
RUN mkdir /share
COPY --from=jar /bmedia_api/target/bmedia_api-${ARG_VERSION}-jar-with-dependencies.jar /bmedia_api.jar
CMD ["java", "-jar", "/bmedia_api.jar", "/mediaDB-Config/db_config.json", "--server.port=38001"]