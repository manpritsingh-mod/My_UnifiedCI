# Multi-CI-Tools: Production-Grade CI-Agnostic Python SDK

## Goal

Build a **production-grade Python SDK** that provides a single, unified business logic layer for CI/CD pipelines. The SDK runs identically on **Jenkins**, **GitHub Actions**, and **local developer machines** — from a single codebase. Focused on **Maven (Java) projects** as the reference implementation, designed for easy extension.

> [!IMPORTANT]
> **Scope simplification per user request:**
> - ✅ Maven project support only (no Python, React, React Native, Node.js)
> - ✅ No mobile support
> - ✅ No Docker orchestration within the SDK (CI layer handles containers)
> - ✅ No `ci-config.yaml` schema — simplified inline config
> - ✅ Stages: Checkout → Setup Env → Install Dep → Lint → Build → Test (Unit + Smoke in parallel) → Notify

---

## Approach Evaluation

Before jumping to the architecture, here are **5 approaches** evaluated for this use case, rated on extensibility, testability, maintenance burden, and production readiness.

### Approach 1: Shell/Makefile Core (Rating: 3/10)

```
Jenkinsfile → make build → build.sh
GHA YAML   → make build → build.sh
```

| Pros | Cons |
|------|------|
| Zero dependencies | Untestable — shell scripts have no unit test framework |
| Every CI can run `make` | Error handling is primitive (`set -e` is not enough) |
| | No structured logging, no retry logic |
| | Windows compatibility nightmare |
| | Parallel execution requires hacky `&` backgrounding |
| | Cannot produce structured reports (JUnit XML parsing in bash?) |

**Verdict:** Fine for 3-stage hobby projects. Breaks at org scale.

---

### Approach 2: Groovy CLI Jar (Rating: 4/10)

```
Repackage existing src/*.groovy as runnable JAR
Jenkinsfile → java -jar unifiedci.jar build
GHA YAML   → java -jar unifiedci.jar build
```

| Pros | Cons |
|------|------|
| Reuses existing Groovy code verbatim | Requires JVM on every CI runner (GHA Ubuntu has it, but adds startup cost) |
| Single language | Groovy ecosystem is shrinking — fewer libraries, fewer contributors |
| | JAR packaging is heavyweight for a CI tool |
| | `vars/*.groovy` still can't port — they use Jenkins DSL |
| | Testing Groovy outside Jenkins is painful (Spock, but tooling is limited) |

**Verdict:** Saves initial porting effort but creates a technology dead-end.

---

### Approach 3: Docker Container CLI (Rating: 6/10)

```
Package all logic in a Docker image
Jenkinsfile → docker run unifiedci:1.0 build
GHA YAML   → docker run unifiedci:1.0 build
```

| Pros | Cons |
|------|------|
| Perfect environment consistency | Docker-in-Docker complexity on Jenkins agents (dind, bind mounts) |
| Version pinning via image tags | Image pull adds 10-30s to every build |
| Any language inside container | Cannot access host filesystem easily (volume mounts required) |
| | Debugging is harder — can't just `python -m pdb` |
| | Network dependency for image pull |
| | Heavy for a tool that just generates and runs shell commands |

**Verdict:** Good for complex build environments, overkill for a command orchestrator.

---

### Approach 4: Node.js / TypeScript SDK (Rating: 5/10)

```
npm package with CLI
Jenkinsfile → npx unifiedci build
GHA YAML   → npx unifiedci build
```

| Pros | Cons |
|------|------|
| GHA is built on Node — native feel | Requires Node.js on every runner (not guaranteed on Jenkins agents) |
| npm ecosystem is vast | Node subprocess handling is less mature than Python's |
| TypeScript gives type safety | Non-trivial for a DevOps team that primarily works in Groovy/Python |
| | `node_modules` bloat for a CI tool |

**Verdict:** Good if your team is JS-native. Not the case here.

---

### Approach 5: Python SDK + CI Adapter Pattern (Rating: 9.5/10) ⭐ WINNER

```
pip install unifiedci
Jenkinsfile → python -m unifiedci build
GHA YAML   → python -m unifiedci build
Local      → python -m unifiedci build
```

| Pros | Cons |
|------|------|
| Python is pre-installed on 99% of CI runners | Python version compatibility (3.8+ needed) |
| `subprocess.run()` is best-in-class for command execution | Slightly slower startup than shell (~200ms) |
| Rich ecosystem: `pydantic`, `tenacity`, `structlog` | Team needs Python familiarity |
| Unit testable locally with `pytest` — no CI needed | |
| `pip install` is 2 seconds, not 30s like Docker pulls | |
| Adapter pattern makes adding GitLab CI a 1-file change | |
| Type hints give IDE autocompletion and safety | |
| Can run on developer laptops with zero CI | |

**Verdict:** The only approach that scores high on every axis: testability, portability, extensibility, developer experience, and production resilience.

---

## Winning Architecture: Deep Dive

### The Adapter Abstraction

The core insight: **Jenkins and GitHub Actions provide the same information through different env vars.** The adapter normalizes this at the boundary so no internal module ever checks which CI is running.

```
┌─────────────────────────────────────────────────────────────────┐
│                  CI Platform (trigger + secrets)                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Jenkins     │  │   GitHub     │  │   Local Dev Machine  │  │
│  │   Jenkinsfile │  │   Actions    │  │   Terminal           │  │
│  │   (10 lines)  │  │   (15 lines) │  │   (0 config)         │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                  │                      │              │
│         └──────────────────┼──────────────────────┘              │
│                            │                                     │
│              python -m unifiedci <stage>                         │
└────────────────────────────┼─────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Python SDK (unifiedci)                       │
│                                                                  │
│  ┌─────────────────────────────────────────────────────┐        │
│  │  CLI Entry Point (cli.py)                            │        │
│  │  Parses args → detects adapter → dispatches stage   │        │
│  └─────────────────────────┬───────────────────────────┘        │
│                            │                                     │
│  ┌─────────────────────────▼───────────────────────────┐        │
│  │  CI Adapter Layer (adapters/)                        │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐            │        │
│  │  │ Jenkins  │ │ GitHub   │ │ Local    │            │        │
│  │  │ Adapter  │ │ Adapter  │ │ Adapter  │            │        │
│  │  └──────────┘ └──────────┘ └──────────┘            │        │
│  │  Normalizes: commit, branch, build#, workspace      │        │
│  └─────────────────────────┬───────────────────────────┘        │
│                            │                                     │
│  ┌─────────────────────────▼───────────────────────────┐        │
│  │  Pipeline Orchestrator (pipeline/)                    │        │
│  │  Manages stage execution, parallel runs, results     │        │
│  │  Stage: checkout → setup → deps → lint → build →    │        │
│  │         test(unit‖smoke) → notify                    │        │
│  └─────────────────────────┬───────────────────────────┘        │
│                            │                                     │
│  ┌──────────┐ ┌────────────▼──┐ ┌───────────┐ ┌──────────────┐ │
│  │ Commands │ │ Executor      │ │ Reporting │ │ Notification │ │
│  │ (maven)  │ │ (subprocess)  │ │ (JUnit)   │ │ (email/slack)│ │
│  └──────────┘ └───────────────┘ └───────────┘ └──────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Env Var Normalization Table

| Concept | Jenkins Env Var | GitHub Actions Env Var | Local Fallback |
|---------|----------------|----------------------|----------------|
| Commit SHA | `GIT_COMMIT` | `GITHUB_SHA` | `git rev-parse HEAD` |
| Branch | `GIT_BRANCH` | `GITHUB_REF_NAME` | `git branch --show-current` |
| Build Number | `BUILD_NUMBER` | `GITHUB_RUN_NUMBER` | `local-{timestamp}` |
| Workspace | `WORKSPACE` | `GITHUB_WORKSPACE` | `os.getcwd()` |
| Build URL | `BUILD_URL` | `$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID` | `file://{cwd}` |
| Job Name | `JOB_NAME` | `GITHUB_WORKFLOW` | `local-build` |
| CI Detection | `JENKINS_URL` set | `GITHUB_ACTIONS=true` | Neither set |
| Is PR? | `CHANGE_ID` set | `GITHUB_EVENT_NAME=pull_request` | `false` |
| PR Number | `CHANGE_ID` | `github.event.pull_request.number` (via `GITHUB_EVENT_PATH`) | `None` |

---

## Proposed Folder Structure

```
Multi-CI-Tools/
├── pyproject.toml                          ← Package metadata, dependencies, entry points
├── README.md                               ← Usage documentation
│
├── unifiedci/                              ← Main Python package
│   ├── __init__.py                         ← Package init, version
│   ├── __main__.py                         ← python -m unifiedci entry point
│   ├── cli.py                              ← CLI argument parsing (argparse)
│   ├── constants.py                        ← Exit codes, stage names, status enums
│   ├── exceptions.py                       ← Custom exception hierarchy
│   │
│   ├── adapters/                           ← CI Adapter Layer
│   │   ├── __init__.py
│   │   ├── base.py                         ← Abstract CIAdapter (ABC)
│   │   ├── jenkins.py                      ← Jenkins env normalization
│   │   ├── github.py                       ← GitHub Actions env normalization
│   │   ├── local.py                        ← Local dev (no CI) adapter
│   │   └── detect.py                       ← Auto-detection logic
│   │
│   ├── config/                             ← Configuration Management
│   │   ├── __init__.py
│   │   ├── models.py                       ← Pydantic models for pipeline config
│   │   ├── reader.py                       ← Read & merge config from file/env/defaults
│   │   └── defaults.py                     ← Default values for all config options
│   │
│   ├── commands/                           ← Command Generators (ports src/*.groovy)
│   │   ├── __init__.py
│   │   ├── maven.py                        ← MavenScript.groovy → Python (1:1 port)
│   │   └── base.py                         ← Base command protocol
│   │
│   ├── executor/                           ← Command Execution Engine
│   │   ├── __init__.py
│   │   ├── runner.py                       ← subprocess.run wrapper with retry/timeout
│   │   └── result.py                       ← Structured execution result (exit code, stdout, stderr, duration)
│   │
│   ├── pipeline/                           ← Pipeline Orchestration
│   │   ├── __init__.py
│   │   ├── orchestrator.py                 ← Main pipeline orchestrator
│   │   ├── stage.py                        ← Stage abstraction (name, status, duration, skip logic)
│   │   ├── context.py                      ← Pipeline context (shared state across stages)
│   │   └── parallel.py                     ← Parallel stage execution (ThreadPoolExecutor)
│   │
│   ├── stages/                             ← Individual Stage Implementations
│   │   ├── __init__.py
│   │   ├── checkout.py                     ← Git checkout (replaces core_github.checkout)
│   │   ├── setup_env.py                    ← Environment setup (replaces core_utils.setupEnv)
│   │   ├── install_deps.py                 ← Dependency installation (replaces core_build.installDep)
│   │   ├── lint.py                         ← Lint execution (replaces lint_utils.runLint)
│   │   ├── build.py                        ← Build execution (replaces core_build.buildLanguages)
│   │   ├── test_unit.py                    ← Unit test execution (replaces core_test.runUnitTest)
│   │   └── test_smoke.py                   ← Smoke test execution
│   │
│   ├── reporting/                          ← Report Generation
│   │   ├── __init__.py
│   │   ├── collector.py                    ← Collect test results from known paths
│   │   ├── junit_parser.py                 ← Parse JUnit XML (replaces sendReport.countTests)
│   │   └── summary.py                      ← Generate human-readable build summary
│   │
│   ├── notification/                       ← Notification Delivery
│   │   ├── __init__.py
│   │   ├── base.py                         ← Abstract notifier interface
│   │   ├── email_notifier.py               ← SMTP email (replaces emailext plugin)
│   │   ├── slack_notifier.py               ← Slack webhook (replaces slackSend plugin)
│   │   └── console_notifier.py             ← Console/log output (always-on fallback)
│   │
│   └── logging/                            ← Structured Logging
│       ├── __init__.py
│       └── logger.py                       ← Replaces logger.groovy with Python logging
│
├── ci-wrappers/                            ← Thin CI-specific entry points
│   ├── jenkins/
│   │   └── Jenkinsfile                     ← ~15 lines: pip install + python -m unifiedci
│   └── github/
│       └── ci.yml                          ← ~20 lines: pip install + python -m unifiedci
│
└── tests/                                  ← SDK tests (run locally, no CI needed)
    ├── __init__.py
    ├── conftest.py                         ← Shared fixtures
    ├── test_cli.py                         ← CLI argument parsing tests
    ├── test_adapters/
    │   ├── test_detect.py                  ← Adapter detection tests
    │   ├── test_jenkins.py                 ← Jenkins adapter tests
    │   ├── test_github.py                  ← GitHub adapter tests
    │   └── test_local.py                   ← Local adapter tests
    ├── test_commands/
    │   └── test_maven.py                   ← Maven command generation tests
    ├── test_executor/
    │   └── test_runner.py                  ← Command runner tests
    ├── test_pipeline/
    │   ├── test_orchestrator.py            ← Pipeline orchestration tests
    │   └── test_parallel.py                ← Parallel execution tests
    ├── test_stages/
    │   ├── test_checkout.py
    │   ├── test_build.py
    │   ├── test_lint.py
    │   └── test_test_unit.py
    ├── test_reporting/
    │   └── test_junit_parser.py            ← JUnit XML parsing tests
    └── test_notification/
        └── test_email.py                   ← Email notification tests
```

**Total: ~50 files, highly modular, each file has a single responsibility.**

---

## User Review Required

> [!IMPORTANT]
> **Decision 1: Config approach.** The original design used `ci-config.yaml`. You mentioned we can ignore it. I propose keeping a **lightweight config** but making it optional — the SDK works with zero config via sensible defaults. Config can come from:
> 1. Environment variables (highest priority)
> 2. A `ci-config.yaml` file (if present)
> 3. Built-in defaults (lowest priority)
>
> **Should we keep this 3-tier config, or go pure environment variables?**

> [!IMPORTANT]
> **Decision 2: Notification channels.** The existing library supports email (via Jenkins `emailext` plugin) and Slack (commented out). For the Python SDK:
> - **Email**: Use Python `smtplib` — requires SMTP server credentials (host, port, user, password) passed as env vars
> - **Slack**: Use Slack Incoming Webhook URL — single env var `SLACK_WEBHOOK_URL`
>
> **Should we implement both email + Slack, or start with just console + Slack (simpler, no SMTP config)?**

> [!WARNING]
> **Decision 3: Python version minimum.** The SDK will use type hints, dataclasses, and `f-strings`. This requires **Python 3.8+**. Your Jenkins agents and GHA runners need Python 3.8+ available. Is this acceptable?

---

## Proposed Changes — Component by Component

---

### Component 1: Package Foundation

#### [NEW] [pyproject.toml](file:///c:/Users/dell/Desktop/Multi-CI-Tools/pyproject.toml)

Modern Python packaging using `pyproject.toml` (replaces `setup.py`). Defines:
- Package name: `unifiedci`
- Version: `0.1.0`
- Entry point: `python -m unifiedci`
- Dependencies: zero required deps (stdlib only for core). Optional: `pydantic` for config validation, `tenacity` for retry logic
- Dev dependencies: `pytest`, `pytest-cov`, `mypy`

#### [NEW] [unifiedci/\_\_init\_\_.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/__init__.py)

Package version and public API surface.

#### [NEW] [unifiedci/\_\_main\_\_.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/__main__.py)

Entry point for `python -m unifiedci`. Delegates to `cli.main()`.

---

### Component 2: CLI Entry Point

#### [NEW] [unifiedci/cli.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/cli.py)

The single universal entry point that replaces ALL `*_template.groovy` files.

```
Usage:
  python -m unifiedci run                    # Run full pipeline (all stages)
  python -m unifiedci run --stage build      # Run single stage
  python -m unifiedci run --stage test       # Run test stage only
  python -m unifiedci run --skip lint        # Skip specific stages
  python -m unifiedci run --parallel         # Enable parallel test execution
  python -m unifiedci info                   # Print detected CI environment
  python -m unifiedci validate              # Validate config without running
```

Key design:
- Uses `argparse` (stdlib, no deps)
- Detects CI adapter automatically
- Loads config from environment + file + defaults
- Dispatches to pipeline orchestrator
- **Exit codes**: `0` = SUCCESS, `1` = FAILURE, `2` = UNSTABLE

#### [NEW] [unifiedci/constants.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/constants.py)

```python
from enum import IntEnum, Enum

class ExitCode(IntEnum):
    SUCCESS = 0
    FAILURE = 1
    UNSTABLE = 2

class StageStatus(str, Enum):
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    UNSTABLE = "UNSTABLE"
    SKIPPED = "SKIPPED"
    RUNNING = "RUNNING"
    PENDING = "PENDING"

class StageName(str, Enum):
    CHECKOUT = "checkout"
    SETUP_ENV = "setup_env"
    INSTALL_DEPS = "install_deps"
    LINT = "lint"
    BUILD = "build"
    UNIT_TEST = "unit_test"
    SMOKE_TEST = "smoke_test"
    NOTIFY = "notify"
```

#### [NEW] [unifiedci/exceptions.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/exceptions.py)

Production exception hierarchy — every exception carries context:

```python
class UnifiedCIError(Exception):
    """Base exception for all UnifiedCI errors."""

class ConfigError(UnifiedCIError):
    """Invalid or missing configuration."""

class StageError(UnifiedCIError):
    """A pipeline stage failed."""
    def __init__(self, stage_name, message, exit_code=1):
        self.stage_name = stage_name
        self.exit_code = exit_code
        super().__init__(f"Stage '{stage_name}' failed: {message}")

class CommandError(UnifiedCIError):
    """A subprocess command failed."""
    def __init__(self, command, exit_code, stdout, stderr, duration):
        self.command = command
        self.exit_code = exit_code
        self.stdout = stdout
        self.stderr = stderr
        self.duration = duration

class AdapterError(UnifiedCIError):
    """CI adapter detection or initialization failed."""

class NotificationError(UnifiedCIError):
    """Notification delivery failed (non-fatal)."""
```

---

### Component 3: CI Adapter Layer

This is the **most critical architectural piece**. It creates a clean boundary between the CI platform and the business logic.

#### [NEW] [unifiedci/adapters/base.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/adapters/base.py)

Abstract base class defining the contract every adapter must fulfill:

```python
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional

@dataclass(frozen=True)
class CIEnvironment:
    """Normalized CI environment — platform-agnostic."""
    ci_name: str              # "jenkins", "github_actions", "local"
    commit_sha: str           # Full 40-char SHA
    branch: str               # Branch name without refs/heads/
    build_number: str         # Unique build identifier
    build_url: str            # URL to build in CI UI
    workspace: str            # Absolute path to project root
    job_name: str             # Pipeline/workflow name
    is_ci: bool               # True if running in any CI
    is_pull_request: bool     # True if triggered by PR
    pr_number: Optional[str]  # PR number if applicable

class CIAdapter(ABC):
    @abstractmethod
    def detect(self) -> bool:
        """Returns True if this adapter's CI platform is detected."""

    @abstractmethod
    def get_environment(self) -> CIEnvironment:
        """Returns normalized CI environment."""

    @abstractmethod
    def export_variable(self, name: str, value: str) -> None:
        """Export a variable in CI-native way (e.g., GHA ::set-output)."""

    @abstractmethod
    def start_group(self, name: str) -> None:
        """Start a collapsible log group (GHA has ::group::, Jenkins has timestamps)."""

    @abstractmethod
    def end_group(self) -> None:
        """End current log group."""

    @abstractmethod
    def set_build_status(self, status: str) -> None:
        """Set build status in CI-native way."""
```

Key edge cases handled in the adapter:
1. **Missing env vars** — every field has a fallback, never raises on missing data
2. **Branch name normalization** — strips `refs/heads/`, `origin/` prefixes
3. **Detached HEAD** — falls back to SHA when no branch is available
4. **Windows paths** — normalizes to forward slashes for cross-platform

#### [NEW] [unifiedci/adapters/jenkins.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/adapters/jenkins.py)

Maps Jenkins-specific env vars: `GIT_COMMIT`, `GIT_BRANCH`, `BUILD_NUMBER`, `WORKSPACE`, `BUILD_URL`, `JOB_NAME`, `CHANGE_ID`.

Special Jenkins handling:
- `GIT_BRANCH` often includes `origin/` prefix → strip it
- `CHANGE_ID` indicates a PR build (Multibranch Pipeline)
- Groups use ANSI color codes for Jenkins console

#### [NEW] [unifiedci/adapters/github.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/adapters/github.py)

Maps GHA-specific env vars: `GITHUB_SHA`, `GITHUB_REF_NAME`, `GITHUB_RUN_NUMBER`, `GITHUB_WORKSPACE`, `GITHUB_REPOSITORY`, `GITHUB_WORKFLOW`, `GITHUB_EVENT_NAME`.

Special GHA handling:
- Uses `::group::` and `::endgroup::` for collapsible log sections
- Uses `::set-output` / `$GITHUB_OUTPUT` for exporting variables
- Reads `GITHUB_EVENT_PATH` JSON file to extract PR number
- Uses `::error::` and `::warning::` for annotations

#### [NEW] [unifiedci/adapters/local.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/adapters/local.py)

For local developer use — reads from git CLI:
- `git rev-parse HEAD` for commit SHA
- `git branch --show-current` for branch
- `os.getcwd()` for workspace
- `is_ci = False` always

#### [NEW] [unifiedci/adapters/detect.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/adapters/detect.py)

Detection priority:
1. `JENKINS_URL` env var → JenkinsAdapter
2. `GITHUB_ACTIONS=true` → GitHubAdapter
3. Neither → LocalAdapter

Edge case: if BOTH are set (e.g., self-hosted GHA runner behind Jenkins), `GITHUB_ACTIONS` wins because it's more specific.

---

### Component 4: Configuration Management

#### [NEW] [unifiedci/config/models.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/config/models.py)

Dataclass-based config models (using stdlib `dataclasses`, no `pydantic` required):

```python
@dataclass
class StageConfig:
    run_unit_tests: bool = True
    run_smoke_tests: bool = True
    run_lint: bool = True

@dataclass  
class MavenConfig:
    build_command: str = "mvn clean install -B"
    test_command: str = "mvn test -B"
    lint_tool: str = "checkstyle"     # "checkstyle" | "spotbugs"
    test_tool: str = "junit"          # "junit" | "surefire"
    smoke_test_command: str = "mvn test -Psmoke -B"
    java_version_command: str = "java -version"
    mvn_version_command: str = "mvn -version"
    install_deps_command: str = "mvn dependency:resolve -B"

@dataclass
class NotificationConfig:
    email_recipients: list[str] = field(default_factory=list)
    slack_webhook_url: str = ""
    slack_channel: str = "#builds"

@dataclass
class PipelineConfig:
    project_language: str = "java-maven"
    stages: StageConfig = field(default_factory=StageConfig)
    maven: MavenConfig = field(default_factory=MavenConfig)
    notification: NotificationConfig = field(default_factory=NotificationConfig)
    parallel_tests: bool = True
    fail_fast: bool = False           # Stop on first stage failure?
    command_timeout: int = 600        # Default command timeout in seconds
    retry_count: int = 0             # Number of retries for flaky commands
```

#### [NEW] [unifiedci/config/reader.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/config/reader.py)

Three-tier config loading:
1. **Defaults** (from `models.py`)
2. **File** (if `ci-config.yaml` exists, merge over defaults)
3. **Environment variables** (highest priority, e.g., `UNIFIEDCI_LINT_TOOL=spotbugs`)

---

### Component 5: Command Generators

#### [NEW] [unifiedci/commands/maven.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/commands/maven.py)

**Direct 1:1 port of `MavenScript.groovy`** — zero behavioral changes:

```python
class MavenCommands:
    """Pure command string generators — zero side effects."""
    
    @staticmethod
    def build() -> str:
        return "mvn clean install -B"
    
    @staticmethod
    def test(tool: str = "junit") -> str:
        match tool:
            case "junit":   return "mvn test -B"
            case "surefire": return "mvn surefire:test -B"
            case _:          return f"mvn test -B -Dtest.tool={tool}"
    
    @staticmethod
    def lint(tool: str = "checkstyle") -> str:
        match tool:
            case "checkstyle": return "mvn checkstyle:check -B"
            case "spotbugs":   return "mvn spotbugs:check -B"
            case _: raise ValueError(f"Unknown lint tool: {tool}")
    
    @staticmethod
    def install_dependencies() -> str:
        return "mvn dependency:resolve -B"
    
    @staticmethod
    def java_version() -> str:
        return "java -version"
    
    @staticmethod
    def mvn_version() -> str:
        return "mvn -version"
    
    @staticmethod
    def smoke_test() -> str:
        return "mvn test -Psmoke -B"
    
    @staticmethod
    def sanity_test() -> str:
        return "mvn test -Psanity -B"
    
    @staticmethod
    def regression_test() -> str:
        return "mvn test -Pregression -B"
```

---

### Component 6: Command Execution Engine

This is what replaces Jenkins' `sh` step. Production-grade with retry, timeout, streaming output, and structured results.

#### [NEW] [unifiedci/executor/result.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/executor/result.py)

```python
@dataclass
class CommandResult:
    command: str
    exit_code: int
    stdout: str
    stderr: str
    duration_seconds: float
    timed_out: bool = False
    
    @property
    def success(self) -> bool:
        return self.exit_code == 0
    
    @property
    def unstable(self) -> bool:
        return self.exit_code == 2
```

#### [NEW] [unifiedci/executor/runner.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/executor/runner.py)

Key features:
- **Real-time stdout streaming** — output appears live in CI console, not buffered until completion
- **Timeout enforcement** — kills stuck processes after configurable timeout
- **Retry with exponential backoff** — for transient failures (network, flaky tests)
- **Cross-platform** — uses `subprocess.run` with shell=False (list args), falls back to shell=True on Windows only when needed
- **Never swallows errors** — always logs command, exit code, duration, stderr

Edge cases handled:
1. **Process hangs** — `timeout` parameter kills after N seconds
2. **Zombie processes** — `process.kill()` + `process.wait()` cleanup
3. **Binary output** — handles UnicodeDecodeError gracefully
4. **Large output** — caps stdout/stderr capture at 10MB to prevent OOM
5. **Signal handling** — catches SIGTERM/SIGINT, kills child processes, exits cleanly
6. **Windows vs Unix** — detects OS and adjusts command execution strategy

---

### Component 7: Pipeline Orchestrator

The heart of the SDK — replaces `javaMaven_template.groovy`.

#### [NEW] [unifiedci/pipeline/context.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/pipeline/context.py)

Shared mutable state carried across all stages:

```python
@dataclass
class PipelineContext:
    config: PipelineConfig
    ci_env: CIEnvironment
    adapter: CIAdapter
    stage_results: dict[str, StageResult]  # stage_name → result
    start_time: float
    overall_status: StageStatus = StageStatus.PENDING
    
    def record_stage(self, name: str, result: StageResult):
        self.stage_results[name] = result
        if result.status == StageStatus.FAILED:
            self.overall_status = StageStatus.FAILED
        elif result.status == StageStatus.UNSTABLE and self.overall_status != StageStatus.FAILED:
            self.overall_status = StageStatus.UNSTABLE
```

#### [NEW] [unifiedci/pipeline/stage.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/pipeline/stage.py)

Stage abstraction with timing, skip logic, and error boundary:

```python
@dataclass
class StageResult:
    name: str
    status: StageStatus
    duration_seconds: float
    error_message: str = ""
    command_results: list[CommandResult] = field(default_factory=list)
```

#### [NEW] [unifiedci/pipeline/parallel.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/pipeline/parallel.py)

Uses `concurrent.futures.ThreadPoolExecutor` for parallel test execution (Unit + Smoke run simultaneously — mirrors the existing Groovy `parallel` block).

Edge cases:
- **Thread safety** — each parallel branch gets its own `CommandResult` list
- **Fail-fast mode** — if one branch fails, cancels the other
- **Output interleaving** — each thread prefixes output with `[unit_test]` or `[smoke_test]`

#### [NEW] [unifiedci/pipeline/orchestrator.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/pipeline/orchestrator.py)

The main orchestration logic:

```python
class PipelineOrchestrator:
    def run(self, context: PipelineContext) -> ExitCode:
        # Sequential stages
        self._run_stage(context, StageName.CHECKOUT, checkout_stage)
        self._run_stage(context, StageName.SETUP_ENV, setup_env_stage)
        self._run_stage(context, StageName.INSTALL_DEPS, install_deps_stage)
        self._run_stage(context, StageName.LINT, lint_stage)
        self._run_stage(context, StageName.BUILD, build_stage)
        
        # Parallel test stages
        if context.config.parallel_tests:
            self._run_parallel(context, [
                (StageName.UNIT_TEST, unit_test_stage),
                (StageName.SMOKE_TEST, smoke_test_stage),
            ])
        else:
            self._run_stage(context, StageName.UNIT_TEST, unit_test_stage)
            self._run_stage(context, StageName.SMOKE_TEST, smoke_test_stage)
        
        # Always runs
        self._run_stage(context, StageName.NOTIFY, notify_stage)
        
        return self._determine_exit_code(context)
```

---

### Component 8: Individual Stage Implementations

Each stage is a standalone function that receives `PipelineContext` and returns `StageResult`.

#### [NEW] [unifiedci/stages/checkout.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/stages/checkout.py)
- On CI: no-op (CI already checked out code)
- On local: verify git repo exists, run `git status`
- Error if workspace directory doesn't exist

#### [NEW] [unifiedci/stages/setup_env.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/stages/setup_env.py)
- Log CI environment (adapter info, commit, branch, build number)
- Run `java -version` and `mvn -version` to verify tools are available
- Fail early if Maven or Java not found (clear error message)

#### [NEW] [unifiedci/stages/install_deps.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/stages/install_deps.py)
- Run `mvn dependency:resolve -B`
- Retry up to 2 times on network failures (Maven central can be flaky)

#### [NEW] [unifiedci/stages/lint.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/stages/lint.py)
- Run checkstyle or spotbugs
- **Critical edge case**: Lint failure → UNSTABLE, not FAILED
- Check for `target/checkstyle-result.xml` to determine if violations exist vs tool crash

#### [NEW] [unifiedci/stages/build.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/stages/build.py)
- Run `mvn clean install -B`
- Build failure is always FAILED (not UNSTABLE)

#### [NEW] [unifiedci/stages/test_unit.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/stages/test_unit.py)
- Run `mvn test -B`
- **Critical edge case**: Test failure → check for `target/surefire-reports/` dir
  - If reports exist → UNSTABLE (tests ran but some failed)
  - If no reports → FAILED (tests couldn't even start)

#### [NEW] [unifiedci/stages/test_smoke.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/stages/test_smoke.py)
- Run `mvn test -Psmoke -B`
- Same UNSTABLE vs FAILED logic as unit tests

---

### Component 9: Reporting

#### [NEW] [unifiedci/reporting/junit_parser.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/reporting/junit_parser.py)

Parses JUnit XML test reports (stdlib `xml.etree.ElementTree` — no deps).

Port of `sendReport.countTests()`:
- Scans `target/surefire-reports/*.xml`
- Extracts: total, passed, failed, skipped, error counts
- Handles malformed XML gracefully (logs warning, returns zeros)

#### [NEW] [unifiedci/reporting/summary.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/reporting/summary.py)

Generates a human-readable build report (port of `sendReport.sendSimpleEmailSlack()`):

```
============================================================
BUILD REPORT
============================================================
Job:     my-maven-app
Build:   #42
Status:  SUCCESS
Time:    2026-04-09 12:30:22
Branch:  main
Commit:  a1b2c3d4
CI:      Jenkins
------------------------------------------------------------
STAGES:
  ✅ Checkout          0.2s
  ✅ Setup Env         1.1s
  ✅ Install Deps     14.3s
  ✅ Lint              8.7s
  ✅ Build            23.5s
  ✅ Unit Test        45.2s
  ✅ Smoke Test       12.8s
  ✅ Notify            0.5s
------------------------------------------------------------
TESTS:
  Total: 156 | Passed: 154 | Failed: 0 | Skipped: 2
LINT ISSUES: 3
============================================================
```

---

### Component 10: Notification

#### [NEW] [unifiedci/notification/email_notifier.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/notification/email_notifier.py)

Uses Python `smtplib` + `email.mime` (stdlib — replaces Jenkins `emailext` plugin).

Config via env vars:
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`
- `EMAIL_RECIPIENTS` (comma-separated)

Edge cases:
- SMTP auth failure → log warning, don't crash the pipeline
- Missing config → skip email silently with log message

#### [NEW] [unifiedci/notification/slack_notifier.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/notification/slack_notifier.py)

Uses `urllib.request` (stdlib — no `requests` dependency) to POST to Slack Incoming Webhook.

Config: `SLACK_WEBHOOK_URL` env var.

#### [NEW] [unifiedci/notification/console_notifier.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/notification/console_notifier.py)

Always-on fallback — prints the build summary to stdout. Useful for local dev.

---

### Component 11: Structured Logging

#### [NEW] [unifiedci/logging/logger.py](file:///c:/Users/dell/Desktop/Multi-CI-Tools/unifiedci/logging/logger.py)

Replaces `logger.groovy` with Python `logging` module:

```python
import logging

def get_logger(name: str = "unifiedci") -> logging.Logger:
    logger = logging.getLogger(name)
    if not logger.handlers:
        handler = logging.StreamHandler()
        formatter = logging.Formatter(
            "[%(levelname)s] [%(asctime)s] %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S"
        )
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
    return logger
```

Matches existing Groovy format: `[INFO] [2026-04-09 12:30:22] Message`

---

### Component 12: Thin CI Wrappers

#### [NEW] [ci-wrappers/jenkins/Jenkinsfile](file:///c:/Users/dell/Desktop/Multi-CI-Tools/ci-wrappers/jenkins/Jenkinsfile)

```groovy
pipeline {
    agent any
    stages {
        stage('Run UnifiedCI') {
            steps {
                sh 'pip install -e . || pip install .'
                sh 'python -m unifiedci run'
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'target/surefire-reports/**/*.xml', allowEmptyArchive: true
            junit testResults: 'target/surefire-reports/**/*.xml', allowEmptyResults: true
        }
    }
}
```

#### [NEW] [ci-wrappers/github/ci.yml](file:///c:/Users/dell/Desktop/Multi-CI-Tools/ci-wrappers/github/ci.yml)

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - run: pip install ./path-to-unifiedci
      - run: python -m unifiedci run
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: target/surefire-reports/
```

---

## Edge Cases & Production Hardening

| Edge Case | How We Handle It |
|-----------|-----------------|
| Maven not installed | `setup_env` stage runs `mvn -version`, fails with clear error: "Maven not found. Install Maven or use a Docker image with Maven pre-installed." |
| Java not installed | Same — `java -version` check in `setup_env` |
| Network failure during `mvn dependency:resolve` | Retry up to 2 times with 5s backoff |
| Test failure (some tests fail) | Check for `target/surefire-reports/` → UNSTABLE exit code (2) |
| Test infrastructure failure (can't start tests) | No surefire reports → FAILED exit code (1) |
| Lint violations found | UNSTABLE (2), not FAILED — matches existing Groovy behavior |
| Lint tool crash | FAILED (1) |
| Pipeline interrupted (Ctrl+C, Jenkins abort) | Signal handler catches SIGTERM/SIGINT, kills child process, produces partial report |
| Missing config file | Works with built-in defaults — no error |
| Both Jenkins and GHA env vars present | GHA wins (more specific detection) |
| Windows path separators | Normalized internally to forward slashes |
| Command produces binary/non-UTF8 output | `errors='replace'` in subprocess decode |
| Notification fails (SMTP down, Slack webhook broken) | Log warning and continue — never crashes pipeline for notification failure |
| Empty test suite (no tests to run) | SUCCESS with 0 test count — matches Maven behavior |
| Parallel branch fails | Other branch continues; overall status is worst status |
| Command timeout | Process killed, stage marked FAILED with "timed out after Ns" message |

---

## Open Questions

> [!IMPORTANT]
> **Q1:** You mentioned no Docker setup needed. Should the Jenkinsfile still handle Docker (pull Maven image from Nexus, run inside container) — with the SDK running *inside* that container? Or should we assume Maven is pre-installed on the Jenkins agent? The existing template heavily uses `docker.withRegistry()` + `image.inside()`.

> [!IMPORTANT]
> **Q2:** The existing library tracks `stageResults` map and sends it in the email. Should notifications include per-stage timing and status, or just overall pass/fail?

> [!TIP]
> **Q3:** Should we include the test suite in the initial implementation, or focus on the SDK first and add tests in a follow-up?

---

## Verification Plan

### Automated Tests

```bash
# Run all SDK tests locally (no CI needed)
python -m pytest tests/ -v --cov=unifiedci --cov-report=term-missing

# Type checking
python -m mypy unifiedci/ --strict

# Test CLI entry point
python -m unifiedci info         # Should print detected environment
python -m unifiedci validate     # Should validate config
```

### Integration Verification

1. **Local**: Run `python -m unifiedci run` in a Maven project directory → verify all stages execute
2. **Mock CI**: Set `JENKINS_URL=http://fake` env var → verify JenkinsAdapter activates
3. **Mock GHA**: Set `GITHUB_ACTIONS=true` → verify GitHubAdapter activates

### Manual Verification

- Push to a GitHub repo with the `ci-wrappers/github/ci.yml` → verify GHA runs successfully
- Configure Jenkins with `ci-wrappers/jenkins/Jenkinsfile` → verify pipeline runs


__________________


# Multi-CI-Tools v1 Implementation Plan

## Summary
- Build a Python `3.10+` package and CLI under `multi_ci_tools` for Maven pipelines that run the same core logic on local machines, Jenkins, and GitHub Actions.
- This is a replacement plan for `implementation_plan.md`: it keeps the strong adapter idea, but narrows v1 so it is easier to implement, safer to operate, and less likely to drift across CI systems.
- Keep the SDK CI-agnostic and container-agnostic. CI wrappers own checkout, tool provisioning, artifact upload, and CI-native status presentation.

## Key Changes From Your Draft
- Remove `checkout` from the SDK pipeline. Checkout stays in Jenkins/GitHub Actions; the SDK starts at `preflight`.
- Drop repo YAML for v1. Configuration comes only from CLI flags and environment variables.
- Do not use `exit code 2 = UNSTABLE` as the process contract. The SDK emits a structured `PipelineResult`; wrappers translate that into Jenkins `UNSTABLE` or GitHub summaries/annotations.
- Keep adapter scope narrow: normalize CI context and provide optional log-group support only. Exporting variables and mutating build state are wrapper concerns, not core adapter requirements.
- Do not run unit and smoke tests in parallel inside one Maven workspace. Default to sequential execution; add CI-level fan-out later if parallelism is needed.
- Split build from test correctly. Default build command is `mvn -B -ntp -DskipTests package`, so tests are not run twice.
- Make smoke tests opt-in by default. Many Maven projects will not have a smoke profile on day one.
- Treat test failures as `fail` by default and lint violations as `warn` by default. Keep both policies configurable.
- Start with a smaller module surface instead of a ~50-file layout. The stable seams for v1 are `CIAdapter`, `BuildBackend`, `PipelineResult`, and `Notifier`.

## Implementation Changes
- Public CLI:
  - `python -m multi_ci_tools run [--stage ...] [--skip-stage ...] [--strict] [--emit-json path] [--emit-summary path]`
  - `python -m multi_ci_tools dry-run`
  - `python -m multi_ci_tools inspect-env`
  - `python -m multi_ci_tools doctor`
- Public config contract:
  - Core env vars: `MCT_ENABLE_LINT`, `MCT_ENABLE_SMOKE`, `MCT_LINT_MODE`, `MCT_TEST_FAILURE_MODE`, `MCT_TIMEOUT_SEC`, `MCT_RETRY_RESOLVE_DEPS`, `MCT_SMOKE_COMMAND`
  - Notification env vars: `MCT_SLACK_WEBHOOK_URL`, `MCT_SMTP_HOST`, `MCT_SMTP_PORT`, `MCT_SMTP_USER`, `MCT_SMTP_PASSWORD`, `MCT_EMAIL_TO`
- Core types/interfaces:
  - `CIContext` normalizes branch, SHA, build URL, workspace, job name, PR metadata, and CI capabilities.
  - `RunConfig` resolves CLI plus env defaults into one validated runtime config.
  - `BuildBackend` owns Maven commands, report paths, and backend-specific capability checks.
  - `StageResult` records `pass|warn|fail|skip`, duration, command metadata, and evidence paths.
  - `PipelineResult` is the machine-readable contract consumed by wrappers, summaries, and notifiers.
- Pipeline behavior:
  - `preflight` validates workspace, `pom.xml`, Java, Maven, and resolved config.
  - `resolve_deps` is an optional cache warm-up stage, retried only for retryable network failures.
  - `lint`, `build`, `unit_test`, `smoke_test`, `publish_summary`, and `notify` run in order, with `notify` always best-effort.
  - Command execution uses streaming subprocess handling, timeout enforcement, bounded capture, and secret redaction.
  - Every run writes `target/multi-ci/ci-result.json` and `target/multi-ci/summary.md` unless overridden.
- CI wrappers/examples:
  - Jenkins example runs the SDK, reads `ci-result.json`, archives reports, maps `warn` to `unstable()`, and maps `fail` to build failure.
  - GitHub Actions example uses `setup-java` and `setup-python`, writes annotations and `GITHUB_STEP_SUMMARY`, uploads artifacts, and fails only on hard failures or when `--strict` promotes warnings.
  - Jenkins container use stays optional at the wrapper layer. The SDK only assumes Java, Maven, and Python exist in the active environment.

## Test Plan
- Unit-test config parsing, adapter detection, branch normalization, PR metadata extraction, and severity-policy resolution.
- Unit-test the Maven backend so build commands skip tests, smoke stays opt-in, and report discovery works for both passing and failing suites.
- Unit-test the executor for streaming, timeout, large output truncation, retry policy, and secret redaction.
- Unit-test pipeline classification for missing `pom.xml`, missing tools, lint violations, failing tests, missing reports, notification failures, and `--strict` promotion.
- Add contract tests for `ci-result.json` and summary output so wrappers and notifications do not drift.
- Add wrapper tests or fixture-driven examples for Jenkins and GitHub Actions result translation.
- Add one integration fixture Maven project covering clean pass, lint warning, unit-test failure, smoke disabled, smoke enabled, and hard tool failure.

## Assumptions And Defaults
- Python minimum is `3.10+` so v1 avoids EOL runtimes while staying easy to provision on Jenkins and GitHub-hosted runners.
- V1 optimizes for a clean re-platform, not a literal Groovy port, because the original Groovy implementation is not present in this repo.
- Tests are part of the initial implementation, not a follow-up, because result classification and CI portability are the highest-risk areas.
- Per-stage timings and statuses are included in JSON, Markdown summary output, Slack, and email.
- Slack and email are included in v1, but both are best-effort and never change the pipeline result.
- CI status mapping intentionally does not rely on `exit 2 = unstable`; GitHub Actions treats any nonzero exit as failure and Jenkins needs explicit unstable handling. References: GitHub exit codes, GitHub workflow commands, Jenkins `sh` step, Jenkins `unstable` step.
