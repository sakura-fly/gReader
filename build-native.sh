#!/bin/bash
# 打包 gReader 为原生二进制文件
set -e

cd "$(dirname "$0")"

# 编译 JAR
mvn clean
mvn package -DskipTests -q

VERSION=$(grep '<version>' pom.xml | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
OUTPUT_DIR="/tmp/greader-native"

# 清理上次输出
rm -rf "$OUTPUT_DIR"
echo "使用 jpackage 打包..."

# 收集参数
JPKG_ARGS=(\
    --name gReader \
    --input target \
    --main-jar "gReader-${VERSION}.jar" \
    --main-class com.gg.Main \
    --type app-image \
    --dest "$OUTPUT_DIR" \
    --java-options "-Xms64m -Xmx256m" \
    --description "gReader - Java TXT Reader" \
    --vendor "gg" \
    --app-version "$VERSION")

# 使用完整 JDK 作为运行时
[ -z "$JAVA_HOME" ] && JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
JPKG_ARGS+=(--runtime-image "$JAVA_HOME")

jpackage "${JPKG_ARGS[@]}"

echo "二进制文件: $OUTPUT_DIR/gReader/bin/gReader"
du -sh "$OUTPUT_DIR/gReader"
mv -r "$OUTPUT_DIR/gReader" target/
echo "已移动到 target/gReader/"
