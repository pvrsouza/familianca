# Front (Vue + Vite): compila frontend/ e o dist vai para os estáticos do Spring Boot.
FROM node:24-slim AS front
WORKDIR /front
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
COPY --from=front /front/dist src/main/resources/static
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
ENV TZ=America/Sao_Paulo
# Sem root: uma falha na aplicação não vira root no container.
RUN useradd --system --uid 10001 --no-create-home familianca
WORKDIR /app
COPY --from=build /src/target/familianca.jar app.jar
USER familianca
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
