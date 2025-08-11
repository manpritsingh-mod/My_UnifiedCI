✅ No Dockerfile maintenance - Official images are maintained by experts ✅ Security updates - Automatically get security patches ✅ Faster setup - Just pull, tag, push ✅ Standard tools - Everything you need is already there ✅ Less complexity - No custom build process

Simple Script for Option 1:


#!/bin/bash

NEXUS_REGISTRY="nexus.company.com:8082"
PROJECT="dev"

echo "🔐 Logging into Nexus..."
docker login $NEXUS_REGISTRY

echo "🐍 Pulling and pushing Python images..."
# Python images
docker pull python:3.8-slim
docker pull python:3.9-slim
docker pull python:3.11-slim

docker tag python:3.8-slim $NEXUS_REGISTRY/$PROJECT/python:3.8
docker tag python:3.9-slim $NEXUS_REGISTRY/$PROJECT/python:3.9
docker tag python:3.11-slim $NEXUS_REGISTRY/$PROJECT/python:3.11
docker tag python:3.11-slim $NEXUS_REGISTRY/$PROJECT/python:latest

docker push $NEXUS_REGISTRY/$PROJECT/python:3.8
docker push $NEXUS_REGISTRY/$PROJECT/python:3.9
docker push $NEXUS_REGISTRY/$PROJECT/python:3.11
docker push $NEXUS_REGISTRY/$PROJECT/python:latest

echo "☕ Pulling and pushing Maven images..."
# Maven images
docker pull maven:3.8.6-openjdk-11
docker pull maven:3.9.0-openjdk-11

docker tag maven:3.8.6-openjdk-11 $NEXUS_REGISTRY/$PROJECT/maven:3.8.6
docker tag maven:3.9.0-openjdk-11 $NEXUS_REGISTRY/$PROJECT/maven:3.9.0
docker tag maven:3.8.6-openjdk-11 $NEXUS_REGISTRY/$PROJECT/maven:latest

docker push $NEXUS_REGISTRY/$PROJECT/maven:3.8.6
docker push $NEXUS_REGISTRY/$PROJECT/maven:3.9.0
docker push $NEXUS_REGISTRY/$PROJECT/maven:latest

echo "🐘 Pulling and pushing Gradle images..."
# Gradle images
docker pull gradle:7.4-jdk11
docker pull gradle:7.6.1-jdk11
docker pull gradle:8.0-jdk11

docker tag gradle:7.4-jdk11 $NEXUS_REGISTRY/$PROJECT/gradle:7.4
docker tag gradle:7.6.1-jdk11 $NEXUS_REGISTRY/$PROJECT/gradle:7.6.1
docker tag gradle:8.0-jdk11 $NEXUS_REGISTRY/$PROJECT/gradle:8.0
docker tag gradle:7.6.1-jdk11 $NEXUS_REGISTRY/$PROJECT/gradle:latest

docker push $NEXUS_REGISTRY/$PROJECT/gradle:7.4
docker push $NEXUS_REGISTRY/$PROJECT/gradle:7.6.1
docker push $NEXUS_REGISTRY/$PROJECT/gradle:8.0
docker push $NEXUS_REGISTRY/$PROJECT/gradle:latest

echo "✅ All official images pulled and pushed to Nexus!"
echo ""
echo "Available images in Nexus:"
echo "- $NEXUS_REGISTRY/$PROJECT/python:3.8, 3.9, 3.11, latest"
echo "- $NEXUS_REGISTRY/$PROJECT/maven:3.8.6, 3.9.0, latest"
echo "- $NEXUS_REGISTRY/$PROJECT/gradle:7.4, 7.6.1, 8.0, latest"
