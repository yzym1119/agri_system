# 多阶段构建 - 构建阶段（构建上下文必须是仓库根目录）
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# 先复制各模块 pom 以利用层缓存
COPY pom.xml .
COPY core-domain/pom.xml core-domain/pom.xml
COPY core-application/pom.xml core-application/pom.xml
COPY core-infrastructure/pom.xml core-infrastructure/pom.xml
COPY api-admin/pom.xml api-admin/pom.xml

# 下载依赖
RUN mvn dependency:go-offline -B

# 复制源代码
COPY core-domain core-domain
COPY core-application core-application
COPY core-infrastructure core-infrastructure
COPY api-admin api-admin

# 从根 pom 构建 api-admin 及其上游模块
RUN mvn clean package -DskipTests -B -pl api-admin -am

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/api-admin/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
