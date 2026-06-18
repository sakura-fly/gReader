#!/bin/bash
# 编译 gReader 为可执行文件
set -e

cd "$(dirname "$0")"
mvn clean package -DskipTests

JAR="target/gReader-1.0.jar"

# 创建启动脚本
cat > target/gReader.sh << 'LAUNCH'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/gReader-1.0.jar" "$@"
LAUNCH
chmod +x target/gReader.sh

echo "编译完成: target/gReader-1.0.jar"
echo "启动命令: java -jar target/gReader-1.0.jar"
