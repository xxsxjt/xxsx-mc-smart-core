#!/bin/bash
# 一键构建所有 MC 版本的 xxsx_builder
# 用法: bash build-all.sh

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "============================================"
echo "  xxsx_builder 多版本构建"
echo "============================================"

# 1. common
echo ""
echo "[1/3] common (Gradle 8.8, JDK 17)..."
cd "$ROOT/common"
export JAVA_HOME="C:/Program Files/Java/jdk-17"
./gradlew build --no-daemon -q
echo "  ✅ common 完成"

# 2. forge-1.20.1
echo ""
echo "[2/3] forge-1.20.1 (Gradle 8.8, JDK 17)..."
cd "$ROOT/forge-1.20.1"
export JAVA_HOME="C:/Program Files/Java/jdk-17"
./gradlew build --no-daemon -q
echo "  ✅ forge-1.20.1 完成"

# 3. neoforge-1.21
echo ""
echo "[3/3] neoforge-1.21 (Gradle 8.14, JDK 21)..."
cd "$ROOT/neoforge-1.21"
export JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.7.6-hotspot"
./gradlew build --no-daemon -q
echo "  ✅ neoforge-1.21 完成"

# 4. forge-26
echo ""
echo "[4/4] forge-26 (Gradle 9.3, JDK 25)..."
cd "$ROOT/forge-26"
export JAVA_HOME="C:/Users/du_ji/.jdks/jdk-25.0.3+9"
./gradlew build --no-daemon -q
echo "  ✅ forge-26 完成"

echo ""
echo "============================================"
echo "  全部构建完成！"
echo "============================================"
echo ""
echo "产物:"
ls -lh "$ROOT/forge-1.20.1/build/libs/"*.jar
ls -lh "$ROOT/neoforge-1.21/build/libs/"*.jar
ls -lh "$ROOT/forge-26/build/libs/"*.jar
