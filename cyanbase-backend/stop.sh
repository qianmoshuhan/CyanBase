#!/bin/bash
# ========================================
# CyanBase 项目停止脚本
# 用法: ./stop.sh
# ========================================

set -e

echo "=========================================="
echo "CyanBase 青渊底座 - 停止服务"
echo "=========================================="
echo ""

# 项目路径
PROJECT_DIR="/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend"
JAR_NAME="cyanbase-backend-1.0.0.jar"
PID_FILE="${PROJECT_DIR}/logs/app.pid"

cd ${PROJECT_DIR}

# 尝试从PID文件获取进程ID
if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null; then
        echo "发现运行中的进程，PID: $PID"
        echo "正在正常停止..."
        kill $PID
        
        # 等待进程结束
        for i in {1..30}; do
            if ! ps -p $PID > /dev/null; then
                echo "✅ 服务已正常停止"
                rm -f $PID_FILE
                exit 0
            fi
            sleep 1
        done
        
        echo "⚠️  正常停止超时，强制杀死进程..."
        kill -9 $PID
        echo "✅ 服务已强制停止"
        rm -f $PID_FILE
        exit 0
    else
        echo "⚠️  PID文件存在但进程不存在: $PID"
        rm -f $PID_FILE
    fi
fi

# 通过进程名查找
PID=$(ps aux | grep ${JAR_NAME} | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo "发现运行中的进程，PID: $PID"
    echo "正在停止..."
    kill $PID
    sleep 5
    
    if ps -p $PID > /dev/null; then
        echo "⚠️  正常停止失败，强制杀死..."
        kill -9 $PID
    fi
    echo "✅ 服务已停止"
else
    echo "⚠️  未发现运行中的服务"
fi

echo ""
echo "=========================================="
echo "停止完成"
echo "=========================================="