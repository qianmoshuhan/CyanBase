#!/bin/bash
# ========================================
# CyanBase 强制查杀进程脚本（兜底方案）
# 用法: ./kill.sh
# ========================================

set -e

echo "=========================================="
echo "CyanBase 青渊底座 - 强制查杀进程"
echo "⚠️  警告：这是兜底方案，会强制杀死进程！"
echo "=========================================="
echo ""

PROJECT_DIR="/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend"
JAR_NAME="cyanbase-backend-1.0.0.jar"
PID_FILE="${PROJECT_DIR}/logs/app.pid"

cd ${PROJECT_DIR}

# 查找所有相关进程
PIDS=$(ps aux | grep ${JAR_NAME} | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "⚠️  未发现运行中的服务进程"
else
    echo "发现以下进程："
    echo "$PIDS"
    echo ""
    echo "正在强制杀死..."
    
    for PID in $PIDS; do
        echo "杀死进程: $PID"
        kill -9 $PID
    done
    
    echo "✅ 所有进程已强制杀死"
fi

# 清理PID文件
if [ -f "$PID_FILE" ]; then
    rm -f $PID_FILE
    echo "✅ PID文件已清理"
fi

echo ""
echo "=========================================="
echo "查杀完成"
echo "=========================================="