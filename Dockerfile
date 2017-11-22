FROM openjdk:8-jdk

RUN mkdir -p /app/visual-search

WORKDIR /app/visual-search

ADD ./src /app/visual-search/src
ADD ./gradlew ./build.gradle /app/visual-search/
ADD ./gradle /app/visual-search/gradle

RUN ./gradlew clean bootJar

CMD java -Xmx500m -Xms500m -XX:+HeapDumpOnOutOfMemoryError -jar -DELASTIC_HOST=$ELASTIC_HOST -DELASTIC_PORT=$ELASTIC_PORT ./build/libs/visual-search.jar

