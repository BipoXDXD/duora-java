# syntax=docker/dockerfile:1
# Imagem de produção do Duora: build com o JDK 25 e runtime com o JRE 25 mais o libvips 8.15 do
# Ubuntu 24.04 (noble), que o boot exige (ProcessadorDeImagemVips).

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

# O Node que o frontend-maven-plugin baixa exige a libatomic, ausente na imagem base.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

# Camada de dependências: muda só quando o pom.xml ou o wrapper mudam.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# Camada do front: Node, pnpm e node_modules mudam só com o package.json ou o lockfile.
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/
RUN ./mvnw -B -q frontend:install-node-and-pnpm@install-node-and-pnpm frontend:pnpm@pnpm-install

COPY . .
# Testes ficam fora da imagem: o Testcontainers exige Docker dentro do build, e o portão é a CI.
RUN ./mvnw -B -q -DskipTests -DskipUnitTests=true package \
    && mv target/duora-*.jar target/app.jar

FROM eclipse-temurin:25-jre-noble

# libvips-tools traz o binário vips (8.15.x no noble); wget serve só ao HEALTHCHECK.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libvips-tools wget \
    && rm -rf /var/lib/apt/lists/*

# UID fixo fora da faixa dos usuários da imagem base (o ubuntu usa 1000).
RUN groupadd --gid 10001 duora \
    && useradd --uid 10001 --gid duora --no-create-home --shell /usr/sbin/nologin duora \
    && mkdir -p /app /dados/imagens \
    && chown duora:duora /dados/imagens

COPY --from=build --chown=root:root /build/target/app.jar /app/app.jar

# O application.properties fixa 127.0.0.1 (dev); no contêiner o proxy chega pela rede do Docker.
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_ADDRESS=0.0.0.0 \
    DUORA_IMAGENS_DIR=/dados/imagens

VOLUME /dados/imagens
EXPOSE 8080
USER duora:duora
WORKDIR /app

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
