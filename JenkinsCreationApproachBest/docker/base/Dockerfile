FROM jenkins/jenkins:lts

# Switch to root to install dependencies
USER root

# Install Docker CLI and basic tools
RUN apt-get update && apt-get install -y \
    docker.io \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Jenkins plugins from plugins.txt
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Switch back to jenkins user
USER jenkins

# Skip initial setup wizard
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
