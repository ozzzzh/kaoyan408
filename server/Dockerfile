# 构建阶段：用与本地一致的 Maven + JDK 17
FROM maven:3.9.10-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# 运行阶段：仅保留 JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m -Dfile.encoding=UTF-8"
COPY --from=build /build/target/server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]