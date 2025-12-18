# Build stage
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

# Directory where MCP server will write logs into is
# exposed to the host computer through this volume:
VOLUME /var/open-meteo-mcp
WORKDIR /opt/open-meteo-mcp

# Name of the jar file is fixed in the pom.xml
COPY --from=build /build/target/mcp-server.jar /opt/open-meteo-mcp/mcp-server.jar
ENTRYPOINT ["java", "-XX:UseSVE=0", "-jar", "mcp-server.jar"]