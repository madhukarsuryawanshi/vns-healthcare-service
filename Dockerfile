FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /workspace/target/vns-healthcare-1.0.1.jar app.jar
EXPOSE 8080
# Allow overriding JVM options via JAVA_OPTS; default tuned for small Render Pro instance
ENV JAVA_OPTS="-Xms512m -Xmx768m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]

