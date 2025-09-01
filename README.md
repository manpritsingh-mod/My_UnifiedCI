# Jenkins Shared Library - Unified CI/CD Pipeline

A comprehensive Jenkins shared library providing standardized CI/CD pipeline templates for multiple programming languages including Java (Maven/Gradle), Python, and React/Node.js applications.

## Table of Contents

- [Overview](#overview)
- [Supported Technologies](#supported-technologies)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Pipeline Templates](#pipeline-templates)
- [Configuration](#configuration)
- [Core Components](#core-components)
- [Docker Integration](#docker-integration)
- [Usage Examples](#usage-examples)
- [Testing & Quality](#testing--quality)
- [Reporting & Notifications](#reporting--notifications)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## Overview

This Jenkins shared library provides a unified approach to CI/CD pipelines across different technology stacks. It eliminates the need to write custom pipeline scripts for each project by providing pre-built, configurable templates that handle:

- **Automated Language Detection**: Automatically detects project type based on build files
- **Docker-based Builds**: All builds run in containerized environments for consistency
- **Comprehensive Testing**: Unit tests, functional tests (smoke, sanity, regression)
- **Code Quality**: Integrated linting and static analysis
- **Reporting**: Allure test reports and email notifications
- **Flexible Configuration**: YAML-based configuration with sensible defaults

## Supported Technologies

| Language | Build Tools | Test Frameworks | Lint Tools | Docker Images |
|----------|-------------|-----------------|------------|---------------|
| **Java** | Maven, Gradle | JUnit, TestNG, Spock | Checkstyle, SpotBugs | Maven 3.8.6, Gradle 7.6.1 |
| **Python** | pip, setuptools | pytest, unittest | pylint, flake8, black | Python 3.11 |
| **React/Node.js** | npm | Jest, Cypress | ESLint, Prettier | Node.js 18 |

## Architecture

```
shared-library/
├── vars/                    # Pipeline templates and core utilities
│   ├── *_template.groovy   # Language-specific pipeline templates
│   ├── core_*.groovy       # Core pipeline functionality
│   ├── lint_utils.groovy   # Code quality utilities
│   ├── logger.groovy       # Centralized logging
│   ├── notify.groovy       # Notification system
│   └── sendReport.groovy   # Report generation
├── src/                     # Reusable classes and utilities
│   ├── *Script.groovy      # Language-specific command generators
│   ├── DockerImageManager.groovy  # Docker image management
│   └── GitHubManager.groovy       # Git operations
└── README.md               # This documentation
```

## Quick Start

### 1. Add Library to Jenkins

In your Jenkins instance:
1. Go to **Manage Jenkins** → **Configure System**
2. Under **Global Pipeline Libraries**, add:
   - **Name**: `unified-ci-library`
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM → Git
   - **Project Repository**: `[your-repo-url]`

### 2. Use in Jenkinsfile

```groovy
@Library('unified-ci-library') _

pipeline {
    agent any
    stages {
        stage('CI/CD Pipeline') {
            steps {
                script {
                    // Auto-detects language and runs appropriate pipeline
                    def config = core_utils.readProjectConfig()
                    
                    // Choose your language template
                    if (config.project_language == 'java-maven') {
                        javaMaven_template(config)
                    } else if (config.project_language == 'python') {
                        python_template(config)
                    } else if (config.project_language == 'react') {
                        react_template(config)
                    }
                }
            }
        }
    }
}
```

### 3. Minimal Configuration

Create `ci-config.yaml` in your project root:

```yaml
# Project Configuration
project_language: java-maven  # or python, react, java-gradle

# Nexus Docker Registry (Required)
nexus:
  url: "https://nexus.company.com:8082"
  registry: "nexus.company.com:8082"
  credentials_id: "nexus-docker-creds"
  project: "dev"

# Stage Control (Optional - defaults to true)
runUnitTests: true
runLintTests: true
runFunctionalTests: true
runSmokeTests: true
runSanityTests: true
runRegressionTests: true

# Tool Selection (Optional - uses defaults)
tool_for_unit_testing:
  java: junit        # or testng
  python: pytest     # or unittest
  react: jest        # or cypress

tool_for_lint_testing:
  java: checkstyle   # or spotbugs
  python: pylint     # or flake8, black
  react: eslint      # or prettier

# Notifications (Optional)
notifications:
  email:
    recipients: ["team@company.com"]
  slack:
    enabled: false
    channel: "#builds"
```

## Pipeline Templates

### Java Maven Template (`javaMaven_template`)

**Features:**
- Maven dependency resolution and caching
- Parallel unit and functional testing
- Checkstyle/SpotBugs integration
- Surefire test reports
- Docker-based builds with Maven 3.8.6

**Usage:**
```groovy
javaMaven_template([
    project_language: 'java-maven',
    tool_for_unit_testing: [java: 'junit'],
    tool_for_lint_testing: [java: 'checkstyle']
])
```

### Java Gradle Template (`javaGradle_template`)

**Features:**
- Gradle wrapper support
- Multi-project builds
- JUnit 5 and Spock support
- Gradle test reports
- Docker-based builds with Gradle 7.6.1

**Usage:**
```groovy
javaGradle_template([
    project_language: 'java-gradle',
    tool_for_unit_testing: [java: 'junit5']
])
```

### Python Template (`python_template`)

**Features:**
- Virtual environment management
- Requirements.txt dependency installation
- pytest/unittest support
- pylint/flake8 code quality
- Coverage reporting
- Docker-based builds with Python 3.11

**Usage:**
```groovy
python_template([
    project_language: 'python',
    tool_for_unit_testing: [python: 'pytest'],
    tool_for_lint_testing: [python: 'pylint']
])
```

### React Template (`react_template`)

**Features:**
- npm dependency management (npm ci for faster installs)
- Jest testing with coverage
- ESLint code quality
- Build artifact generation
- Docker-based builds with Node.js 18

**Usage:**
```groovy
react_template([
    project_language: 'react',
    tool_for_unit_testing: [react: 'jest'],
    tool_for_lint_testing: [react: 'eslint']
])
```

## Configuration

### Language Detection

The library automatically detects your project language:

| File Present | Detected Language |
|--------------|-------------------|
| `pom.xml` | java-maven |
| `build.gradle` or `build.gradle.kts` | java-gradle |
| `requirements.txt`, `setup.py`, `pyproject.toml` | python |
| `package.json` (with React dependencies) | react |
| `package.json` (without React) | nodejs |

### Docker Configuration

All builds run in Docker containers pulled from your Nexus registry:

```yaml
nexus:
  url: "https://nexus.company.com:8082"      # Registry URL with protocol
  registry: "nexus.company.com:8082"         # Registry without protocol
  credentials_id: "nexus-docker-creds"       # Jenkins credentials ID
  project: "dev"                             # Nexus project/repository

# Version overrides (optional)
java-maven_version: "3.8.6"    # Maven version
java-gradle_version: "7.6.1"   # Gradle version  
python_version: "3.11"         # Python version
react_version: "18"            # Node.js version
```

### Stage Control

Enable/disable pipeline stages:

```yaml
runUnitTests: true          # Run unit tests
runLintTests: true          # Run code quality checks
runFunctionalTests: true    # Enable functional test suite
runSmokeTests: true         # Run smoke tests
runSanityTests: true        # Run sanity tests
runRegressionTests: true    # Run regression tests
```

## Core Components

### Core Build (`core_build.groovy`)

Handles dependency installation and building for all supported languages:

```groovy
// Install dependencies
core_build.installDependencies('java', 'maven', config)
core_build.installDependencies('python', 'pip', config)
core_build.installDependencies('react', 'npm', config)

// Build projects
core_build.buildLanguages('java-maven', config)
core_build.buildLanguages('python', config)
core_build.buildLanguages('react', config)
```

### Core Test (`core_test.groovy`)

Executes unit tests with proper result handling:

```groovy
def result = core_test.runUnitTest(config)
// Returns: 'SUCCESS', 'UNSTABLE', or 'FAILED'
```

### Lint Utils (`lint_utils.groovy`)

Runs code quality checks:

```groovy
def lintResult = lint_utils.runLint(config)
// Returns: 'SUCCESS', 'UNSTABLE', or 'FAILED'
```

### Core Utils (`core_utils.groovy`)

Provides utility functions:

```groovy
// Language detection
def language = core_utils.detectProjectLanguage()

// Configuration management
def config = core_utils.readProjectConfig()
def defaultConfig = core_utils.getDefaultConfig()

// Stage control
if (core_utils.shouldExecuteStage('unittest', config)) {
    // Run unit tests
}
```

## Docker Integration

### Image Management

The `DockerImageManager` class handles all Docker operations:

```groovy
// Get image configuration
def imageConfig = DockerImageManager.getImageConfig('java-maven', config)
// Returns: [tool: 'maven', version: '3.8.6', imagePath: 'nexus.company.com:8082/dev/maven:3.8.6', ...]

// Validate configuration
def validation = DockerImageManager.validateDockerConfig(config)
// Returns: [valid: true/false, message: '...']
```

### Supported Images

| Language | Default Image | Available Versions |
|----------|---------------|-------------------|
| Java Maven | `nexus.company.com:8082/dev/maven:3.8.6` | 3.8.6, 3.9.0, 3.9.6, latest |
| Java Gradle | `nexus.company.com:8082/dev/gradle:7.6.1` | 7.4, 7.6.1, 8.0, latest |
| Python | `nexus.company.com:8082/dev/python:3.11` | 3.8, 3.9, 3.11, latest |
| React/Node.js | `nexus.company.com:8082/dev/node:18` | 16, 18, 20, latest |

## Usage Examples

### Basic Java Maven Project

```groovy
@Library('unified-ci-library') _

pipeline {
    agent any
    stages {
        stage('Build & Test') {
            steps {
                script {
                    javaMaven_template([
                        project_language: 'java-maven',
                        nexus: [
                            url: 'https://nexus.company.com:8082',
                            registry: 'nexus.company.com:8082',
                            credentials_id: 'nexus-creds'
                        ]
                    ])
                }
            }
        }
    }
}
```

### Python Project with Custom Tools

```groovy
@Library('unified-ci-library') _

pipeline {
    agent any
    stages {
        stage('Python CI') {
            steps {
                script {
                    python_template([
                        project_language: 'python',
                        tool_for_unit_testing: [python: 'pytest'],
                        tool_for_lint_testing: [python: 'flake8'],
                        runRegressionTests: false  // Skip regression tests
                    ])
                }
            }
        }
    }
}
```

### React Project with Notifications

```groovy
@Library('unified-ci-library') _

pipeline {
    agent any
    stages {
        stage('React Build') {
            steps {
                script {
                    react_template([
                        project_language: 'react',
                        notifications: [
                            email: [recipients: ['frontend-team@company.com']],
                            slack: [enabled: true, channel: '#frontend-builds']
                        ]
                    ])
                }
            }
        }
    }
}
```

## Testing & Quality

### Test Types

1. **Unit Tests**: Fast, isolated tests for individual components
2. **Smoke Tests**: Basic functionality verification
3. **Sanity Tests**: Core feature validation
4. **Regression Tests**: Comprehensive test suite

### Test Commands by Language

**Java Maven:**
```bash
mvn test                    # Unit tests
mvn test -Psmoke           # Smoke tests
mvn test -Psanity          # Sanity tests
mvn test -Pregression      # Regression tests
```

**Java Gradle:**
```bash
gradle test                # Unit tests
gradle test -Psmoke        # Smoke tests
gradle test -Psanity       # Sanity tests
gradle test -Pregression   # Regression tests
```

**Python:**
```bash
venv/bin/pytest --verbose                    # Unit tests
venv/bin/pytest tests/smoke/ -v             # Smoke tests
venv/bin/pytest tests/sanity/ -v            # Sanity tests
venv/bin/pytest tests/regression/ -v        # Regression tests
```

**React:**
```bash
npm test -- --coverage --watchAll=false     # Unit tests
npm run test:smoke                          # Smoke tests
npm run test:sanity                         # Sanity tests
npm run test:regression                     # Regression tests
```

### Code Quality Tools

**Java:**
- **Checkstyle**: Code style and formatting
- **SpotBugs**: Static analysis for bug detection

**Python:**
- **pylint**: Comprehensive code analysis
- **flake8**: Style guide enforcement
- **black**: Code formatting

**React:**
- **ESLint**: JavaScript/TypeScript linting
- **Prettier**: Code formatting

## Reporting & Notifications

### Allure Reports

Automatic test report generation using Allure:

- Aggregates test results from all frameworks
- Provides detailed test execution reports
- Accessible through Jenkins UI
- Supports trend analysis

### Email Notifications

Comprehensive build reports sent via email:

```
BUILD REPORT
============

Job: my-java-app
Build: #42
Status: SUCCESS
Time: 2024-01-15 14:30:22
URL: https://jenkins.company.com/job/my-java-app/42/

STAGES:
 - Checkout: SUCCESS
 - Setup: SUCCESS
 - Install Dependencies: SUCCESS
 - Lint: SUCCESS
 - Build: SUCCESS
 - Unit Test: SUCCESS
 - Functional Tests: SUCCESS
 - Generate Reports: SUCCESS

TESTS:
- Total: 156
- Passed: 154
- Failed: 0
- Skipped: 2

LINT ISSUES: 3

============ Build SUCCESS ============
```

### Slack Integration (Optional)

Real-time build notifications to Slack channels with:
- Build status with color coding
- Test summary
- Direct links to Jenkins build
- Customizable channels per project

## Best Practices

### Project Structure

**Java Maven:**
```
project/
├── pom.xml
├── src/main/java/
├── src/test/java/
└── ci-config.yaml
```

**Java Gradle:**
```
project/
├── build.gradle
├── src/main/java/
├── src/test/java/
└── ci-config.yaml
```

**Python:**
```
project/
├── requirements.txt
├── src/
├── tests/
│   ├── smoke/
│   ├── sanity/
│   └── regression/
└── ci-config.yaml
```

**React:**
```
project/
├── package.json
├── src/
├── public/
├── tests/
└── ci-config.yaml
```

### Configuration Management

1. **Use ci-config.yaml**: Always provide project configuration
2. **Environment-specific configs**: Use different configs for dev/staging/prod
3. **Minimal configuration**: Library provides sensible defaults
4. **Version pinning**: Specify exact tool versions for reproducible builds

### Security

1. **Credentials**: Store all sensitive data in Jenkins credentials
2. **Docker images**: Use private Nexus registry for security
3. **Network isolation**: Builds run in isolated Docker containers
4. **Audit trail**: All pipeline activities are logged

## Troubleshooting

### Common Issues

**Docker Image Pull Failures:**
```
Error: Failed to pull image nexus.company.com:8082/dev/maven:3.8.6
```
**Solution:** Verify Nexus credentials and network connectivity

**Missing Dependencies:**
```
Error: Could not resolve dependencies
```
**Solution:** Check if requirements.txt/pom.xml/package.json exists and is valid

**Test Failures:**
```
Build marked as UNSTABLE due to test failures
```
**Solution:** Review test reports in Jenkins UI and fix failing tests

**Configuration Errors:**
```
Error: Missing Nexus configuration
```
**Solution:** Ensure nexus.url, nexus.registry, and nexus.credentials_id are set

### Debug Mode

Enable detailed logging by adding to your config:

```yaml
debug: true
```

### Log Analysis

Check Jenkins console output for detailed execution logs:
- `[INFO]` - Normal operations
- `[WARNING]` - Non-critical issues
- `[ERROR]` - Critical failures requiring attention

## Contributing

### Development Setup

1. Clone the repository
2. Create feature branch: `git checkout -b feature/new-functionality`
3. Make changes following existing patterns
4. Test with sample projects
5. Submit pull request

### Adding New Language Support

1. Create new template in `vars/`: `newlang_template.groovy`
2. Add script class in `src/`: `NewLangScript.groovy`
3. Update `core_utils.groovy` for language detection
4. Add Docker image support in `DockerImageManager.groovy`
5. Update documentation

### Code Standards

- Follow existing Groovy conventions
- Add comprehensive logging
- Include error handling
- Document all public methods
- Maintain backward compatibility

---

**Version:** 1.0.0  
**Last Updated:** January 2025  
**Maintainer:** DevOps Team  
**Support:** Create issue in repository or contact devops@company.com