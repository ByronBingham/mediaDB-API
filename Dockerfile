# syntax=docker/dockerfile:1

FROM ubuntu:23.04 AS jar
RUN apt-get update
RUN apt-get install openjdk-11-jdk -y
RUN apt-get install maven -y

COPY ./bmedia_api /bmedia_api
WORKDIR /bmedia_api
RUN mvn package

FROM adoptopenjdk/openjdk11
RUN mkdir /share
COPY --from=jar /bmedia_api/target/bmedia_api-1.0-SNAPSHOT-jar-with-dependencies.jar /bmedia_api.jar
CMD ["java", "-jar", "/bmedia_api.jar", "/db_config.json", "--server.port=38001"]