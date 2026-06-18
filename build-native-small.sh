#!/bin/bash
# 为有 Java 环境的情况打包精简二进制（jlink 裁剪 JRE）
set -e
cd "$(dirname "$0")"

# 编译
mvn clean
mvn package -DskipTests -q

VERSION=$(grep '<version>' pom.xml | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
OUTPUT_DIR="/tmp/greader-native-small"
RUNTIME_DIR="/tmp/greader-runtime"

rm -rf "$OUTPUT_DIR" "$RUNTIME_DIR"

# 用 jlink 创建精简 JRE，仅包含所需模块
echo "创建精简 JRE..."
[ -z "$JAVA_HOME" ] && JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
jlink \
    --output "$RUNTIME_DIR" \
    --add-modules java.desktop,java.prefs,java.datatransfer,java.xml \
    --strip-native-commands \
    --no-man-pages \
    --no-header-files \
    --compress=2

echo "JRE 大小: $(du -sh $RUNTIME_DIR | cut -f1)"

# 用精简 JRE 打包
echo "打包..."
jpackage \
    --name gReader \
    --input target \
    --main-jar "gReader-${VERSION}.jar" \
    --main-class com.gg.Main \
    --type app-image \
    --dest "$OUTPUT_DIR" \
    --runtime-image "$RUNTIME_DIR" \
    --java-options "-Xms64m -Xmx256m"

echo "二进制: $OUTPUT_DIR/gReader/bin/gReader"
du -sh "$OUTPUT_DIR/gReader"
rm -rf target/gReader
cp -r "$OUTPUT_DIR/gReader" target/
echo "已复制到 target/gReader/"
