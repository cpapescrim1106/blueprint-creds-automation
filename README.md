# Blueprint Creds Automation

This repository contains tools to automate the retrieval of AWS credentials from the Blueprint OMS client and trigger/download reports via the backend API.

## Installation

1.  **Dependencies:**
    Run the installation script to copy the necessary Java binaries and client libraries from your existing installation.
    ```bash
    ./install_dependencies.sh /path/to/original/project
    ```

2.  **Python:**
    Create a virtual environment and install requirements.
    ```bash
    python3 -m venv .venv
    source .venv/bin/activate
    pip install -r requirements.txt
    ```

3.  **Credentials:**
    Edit `.oms_credentials` and add your OMS username and password.
    ```bash
    nano .oms_credentials
    ```

## Usage

### 1. Harvest Credentials
This script launches the OMS client in a headless-like mode (with an auto-login agent), captures the AWS session keys, and saves them to `.env.aws`.

```bash
./bin/1_harvest_keys.sh
```

### 2. Trigger Report
Sends a report request payload to the SQS queue using the harvested credentials.

```bash
# You need a JSON payload file (e.g., payload.json)
./bin/2_trigger_report.sh payload.json
```

### 3. Download Result
Downloads the generated report from S3.

```bash
./bin/3_download_result.sh <S3_KEY> --wait
```

## Structure

- `agent/`: Source code for the Java Instrumentation Agent (Auto-Login & Logger).
- `bin/`: Main executable scripts.
- `deps/`: (Created by install script) Contains JDK and Client JARs.
- `lib/`: Python helper scripts.
