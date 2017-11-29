FROM openjdk:8
ENV APP_HOME=/root/dev/service-api/
RUN mkdir -p $APP_HOME/src/main/java
WORKDIR $APP_HOME
COPY build.gradle build-quality.gradle gradlew gradlew.bat $APP_HOME
COPY gradle $APP_HOME/gradle
COPY . .
RUN ./gradlew build -ParchiveName=service-api.jar -x test -x findbugsMain -x javadoc -x jacocoTestReport


FROM openjdk:8-jre
ENV JAVA_OPTS="-Xmx1g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp \-Djava.security.egd=file:/dev/./urandom"
ENV JAVA_APP=target/app.jar
WORKDIR /root/
RUN apt-get update && \
    apt-get install -y fonts-noto net-tools && \
    rm -rf /var/lib/apt/lists/*

RUN sh -c "echo '#!/bin/sh \n\
exec java $JAVA_OPTS -jar $JAVA_APP' > /start.sh && chmod +x /start.sh"

VOLUME /tmp

COPY --from=0 /root/dev/service-api/build/libs/service-api.jar target/app.jar
RUN sh -c 'touch target/app.jar'
EXPOSE 8080
ENTRYPOINT ["/start.sh"]