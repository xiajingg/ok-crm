# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先拷贝所有 pom，让依赖下载这一层可以被缓存
COPY pom.xml ./
COPY platform/platform-common/pom.xml platform/platform-common/
COPY platform/platform-tenant/pom.xml platform/platform-tenant/
COPY platform/platform-mybatis/pom.xml platform/platform-mybatis/
COPY platform/platform-security/pom.xml platform/platform-security/
COPY platform/platform-web/pom.xml platform/platform-web/
COPY modules/module-tenant/pom.xml modules/module-tenant/
COPY modules/module-iam/pom.xml modules/module-iam/
COPY modules/module-customer/pom.xml modules/module-customer/
COPY modules/module-pool/pom.xml modules/module-pool/
COPY apps/crm-boot/pom.xml apps/crm-boot/

RUN mvn -B -q dependency:go-offline -pl apps/crm-boot -am || true

# 再拷贝源码编译
COPY platform ./platform
COPY modules ./modules
COPY apps ./apps

# 想构建「只含已购买模块」的交付包，把 -Pedition-basic / -Pedition-pro 加进来即可
RUN mvn -B -DskipTests package -pl apps/crm-boot -am

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# 时区跟随容器环境变量，避免日志时间对不上
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=build /build/apps/crm-boot/target/ok-crm.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
