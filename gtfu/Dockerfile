FROM gradle:6.8.1-jdk15

WORKDIR /usr/src/gtfu

COPY build.gradle build.gradle
COPY gradlew gradlew

COPY src/ src/
COPY scripts/ scripts/

RUN gradle --no-daemon distZip