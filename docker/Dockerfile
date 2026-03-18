# Stage 1: Build the plugin from source
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
# Cache dependencies first
RUN mvn dependency:go-offline -B || true
COPY src/ src/
RUN mvn clean package -Dmaven.test.skip=true -B

# Stage 2: Jenkins with the plugin installed
FROM jenkins/jenkins:2.492.1-lts-jdk17

# Skip the setup wizard
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# Install required plugins
COPY docker/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Create admin user on first boot
COPY docker/init-admin.groovy /usr/share/jenkins/ref/init.groovy.d/init-admin.groovy

# Install the Last9 plugin from the build stage
COPY --from=builder /build/target/last9.hpi /usr/share/jenkins/ref/plugins/last9.hpi
