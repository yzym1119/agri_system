# 多阶段构建 - 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# 复制 pom.xml 文件
COPY pom.xml .
COPY core-domain/pom.xml ./core-domain/
COPY core-application/pom.xml ./core-application/
COPY core-infrastructure/pom.xml ./core-infrastructure/
COPY api-admin/pom.xml ./api-admin/
COPY api-client/pom.xml ./api-client/

# 下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY core-domain ./core-domain
COPY core-application ./core-application
COPY core-infrastructure ./core-infrastructure
COPY api-admin ./api-admin
COPY api-client ./api-client

# 构建项目
RUN mvn clean package -DskipTests -B

# 运行阶段 - API Admin
FROM eclipse-temurin:17-jre-alpine AS api-admin

WORKDIR /app

COPY --from=build /app/api-admin/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

# 运行阶段 - API Client
FROM eclipse-temurin:17-jre-alpine AS api-client

WORKDIR /app

COPY --from=build /app/api-client/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]






















