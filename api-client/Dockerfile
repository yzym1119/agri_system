# 多阶段构建 - 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# 复制父 pom.xml
COPY ../pom.xml ./pom.xml
COPY ../core-domain/pom.xml ./core-domain/pom.xml
COPY ../core-application/pom.xml ./core-application/pom.xml
COPY ../core-infrastructure/pom.xml ./core-infrastructure/pom.xml
COPY pom.xml ./api-client/pom.xml

# 下载依赖
RUN mvn dependency:go-offline -B

# 复制源代码
COPY ../core-domain ./core-domain
COPY ../core-application ./core-application
COPY ../core-infrastructure ./core-infrastructure
COPY . ./api-client

# 构建项目
RUN mvn clean package -DskipTests -B -f api-client/pom.xml

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/api-client/target/*.jar app.jar

EXPOSE 8081

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]






















