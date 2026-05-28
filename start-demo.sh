#!/bin/bash
# 点餐系统 Demo 启动脚本
# 使用 H2 内存数据库，无需 Docker

JAVA_HOME="${JAVA_HOME:-/tmp/jdk-21.0.6+7}"
JAR="$HOME/workspaceForOpenclaw/ordering-system/backend/target/ordering-backend-1.0.0.jar"

if [ ! -f "$JAR" ]; then
  echo "构建 jar..."
  cd "$HOME/workspaceForOpenclaw/ordering-system/backend"
  $JAVA_HOME/bin/java -jar /tmp/apache-maven-3.8.8/lib/maven-launcher.jar 2>/dev/null || \
  "$JAVA_HOME/bin/javac" -version && echo "请先执行 mvn package"
  exit 1
fi

echo "🚀 启动点餐系统后端..."
nohup $JAVA_HOME/bin/java -jar "$JAR" --spring.profiles.active=demo \
  > /tmp/ordering-backend.log 2>&1 &

PID=$!
echo "PID: $PID"

# 等待启动
for i in $(seq 1 15); do
  sleep 1
  curl -s --noproxy '*' -o /dev/null http://127.0.0.1:8080/api/menu && break
done

echo "✅ 服务已启动: http://127.0.0.1:8080"
echo "📋 Swagger:  http://127.0.0.1:8080/swagger-ui.html"
echo "💾 H2 Console: http://127.0.0.1:8080/h2-console"
echo ""
echo "⚠️  注意：curl 需要加 --noproxy '*' 参数避免走代理"
echo ""
echo "🔄 切换到 MySQL+Redis 请执行: docker compose up -d && java -jar ... --spring.profiles.active=prod"
