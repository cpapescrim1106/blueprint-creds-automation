#!/bin/bash
set -euo pipefail

# Script to populate the 'deps' directory with the required binaries.
# Usage: ./install_dependencies.sh <path_to_original_project_root>

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <path_to_source_project>"
    echo "Example: $0 ~/Documents/BlueprintProject"
    exit 1
fi

SOURCE_DIR="$1"
DEPS_DIR="$(cd "$(dirname "$0")" && pwd)/deps"

if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: Source directory '$SOURCE_DIR' does not exist."
    exit 1
fi

echo "Installing dependencies from $SOURCE_DIR to $DEPS_DIR..."

mkdir -p "$DEPS_DIR"

# Copy JDK
if [ -d "$SOURCE_DIR/jdk8u462-b08" ]; then
    echo "Copying JDK..."
    rm -rf "$DEPS_DIR/jdk"
    cp -R "$SOURCE_DIR/jdk8u462-b08" "$DEPS_DIR/jdk"
else
    echo "Error: JDK not found in source."
    exit 1
fi

# Copy Client Libs
if [ -d "$SOURCE_DIR/client_lib" ]; then
    echo "Copying client_lib..."
    rm -rf "$DEPS_DIR/client_lib"
    cp -R "$SOURCE_DIR/client_lib" "$DEPS_DIR/client_lib"
else
    echo "Error: client_lib not found in source."
    exit 1
fi

# Copy Client Runtime
if [ -d "$SOURCE_DIR/client_runtime" ]; then
    echo "Copying client_runtime..."
    rm -rf "$DEPS_DIR/client_runtime"
    cp -R "$SOURCE_DIR/client_runtime" "$DEPS_DIR/client_runtime"
else
    echo "Error: client_runtime not found in source."
    exit 1
fi

# Copy Classpath File
if [ -f "$SOURCE_DIR/client_classpath.txt" ]; then
    echo "Copying client_classpath.txt..."
    cp "$SOURCE_DIR/client_classpath.txt" "$DEPS_DIR/client_classpath.txt"
else
    echo "Error: client_classpath.txt not found in source."
    exit 1
fi

echo "✅ Dependencies installed successfully."
