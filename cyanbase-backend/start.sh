#!/bin/bash
# ========================================
# CyanBase 项目启动脚本（自带编译打包）
# 用法: ./start.sh
# ========================================
set -e
echo "=========================================="
echo "CyanBase 青渊底座 - 启动服务"
echo "=========================================="
echo ""
# 项目路径
PROJECT_DIR="/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend"
JAR_NAME="cyanbase-backend-1.0.0.jar"
LOG_DIR="${PROJECT_DIR}/logs"
PID_FILE="${LOG_DIR}/app.pid"
cd ${PROJECT_DIR}
# 检查是否已在运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null; then
        echo "⚠️  服务已在运行，PID: $PID"
        echo "如需重启，请先执行: ./stop.sh 或 ./restart.sh"
        exit 0
    else
        echo "发现过期PID文件，清理中..."
        rm -f $PID_FILE
    fi
fi
# ======================
# 🔥 自动编译 + 打包
# ======================
echo "【1】开始编译打包项目..."
mvn clean package -DskipTests
echo "✅ 编译打包完成"
echo ""
# 检查JAR文件是否存在
if [ ! -f "target/${JAR_NAME}" ]; then
    echo "❌ JAR文件不存在: target/${JAR_NAME}"
    exit 1
fi
# 创建日志目录
mkdir -p ${LOG_DIR}
# 启动服务
echo "【2】正在启动服务..."
# 日志统一为 cyanbase.log
nohup java -jar target/${JAR_NAME} > ${LOG_DIR}/cyanbase.log 2>&1 &
NEW_PID=$!
echo $NEW_PID > $PID_FILE
echo "✅ 服务已启动，PID: $NEW_PID"
echo "日志文件: ${LOG_DIR}/cyanbase.log"
echo "查看日志: tail -f ${LOG_DIR}/cyanbase.log"
echo ""
echo "等待服务初始化..."
sleep 10
# 检查启动状态
if ps -p $NEW_PID > /dev/null; then
    echo "✅ 服务运行正常"
else
    echo "❌ 服务启动失败！请查看日志: ${LOG_DIR}/cyanbase.log"
    rm -f $PID_FILE
    exit 1
fi

