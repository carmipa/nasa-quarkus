# =============================================================================
# Imagem de produção — build multi-estágio, compilado na própria VPS.
#
# POR QUE MULTI-ESTÁGIO: o `src/main/docker/Dockerfile.jvm` que o Quarkus gera
# exige que alguém tenha rodado `./gradlew build` ANTES, na máquina certa, com o
# JDK certo. Isso torna a imagem dependente do estado do host — e um deploy que
# depende do que sobrou de ontem não é reproduzível. Aqui o `git clone` basta.
# =============================================================================
FROM eclipse-temurin:25-jdk AS build

WORKDIR /src

# As camadas vêm em ordem de estabilidade: wrapper e declaração de dependências
# mudam pouco, `src` muda a cada commit. Trocado de ordem, cada alteração de
# código baixaria o Gradle e as dependências de novo.
COPY gradle gradle
COPY gradlew settings.gradle build.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon --version

COPY src src
COPY docs/documentacao docs/documentacao
# `-x test`: a suíte roda no CI e na máquina de quem desenvolve. Repeti-la aqui
# poria 127 testes no caminho crítico de todo deploy sem descobrir nada novo.
RUN ./gradlew --no-daemon clean build -x test

# -----------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

ENV LANGUAGE='en_US:en'
ENV LANG='en_US.UTF-8'
ENV TZ='UTC'

COPY --from=build --chown=185 /src/build/quarkus-app/lib/      /deployments/lib/
COPY --from=build --chown=185 /src/build/quarkus-app/*.jar     /deployments/
COPY --from=build --chown=185 /src/build/quarkus-app/app/      /deployments/app/
COPY --from=build --chown=185 /src/build/quarkus-app/quarkus/  /deployments/quarkus/
# A página pública de documentação lê os `.md` do DISCO em tempo de execução
# (`nasa.docs.pasta`, padrão `docs/documentacao`, relativo ao diretório de
# trabalho, que aqui é /deployments). Sem esta cópia a vitrine sobe com o índice
# vazio — e a documentação é requisito público, não enfeite.
COPY --from=build --chown=185 /src/docs/documentacao/ /deployments/docs/documentacao/
COPY --chown=185 --chmod=755 entrada.sh /deployments/entrada.sh

EXPOSE 8080
USER 185

ENV GC_CONTAINER_OPTIONS="-XX:+UseSerialGC"

# As duas primeiras flags NÃO são ajuste fino, são condição de arranque:
#   -Duser.timezone=UTC ......... a CatracaDeFusoUtc derruba o boot sem ela, e
#                                 está certa: o carimbo do log é escrito pelo
#                                 framework de logging com o fuso da JVM, não
#                                 pelo código do domínio.
#   --enable-native-access ...... o driver do SQLite carrega biblioteca nativa
#                                 por `System.load`; sem a declaração o Java 25
#                                 avisa hoje e bloqueia numa versão futura.
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 \
    -Duser.timezone=UTC \
    --enable-native-access=ALL-UNNAMED \
    -Dfile.encoding=UTF-8 \
    -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
    -XX:MaxMetaspaceSize=192m"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/deployments/entrada.sh" ]
