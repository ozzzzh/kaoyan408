# 构建阶段：用与本地一致的 Maven + JDK 17
FROM maven:3.9.10-eclipse-temurin-17 AS build
WORKDIR /build

# 先放镜像配置再下载依赖：国内部署环境直连 Maven 中央仓库很慢或不通。
# 该文件纳入版本控制，避免服务器上出现「本地修改与 git pull 冲突」。
# 若你的环境能直连中央仓库，可删除这行（Maven 会退回内置默认源）。
COPY settings.xml /root/.m2/settings.xml

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