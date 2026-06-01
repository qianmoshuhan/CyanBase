#!/bin/bash
# ========================================
# CyanBase 一键打包部署脚本
# 用法: ./deploy.sh
# ========================================

set -e

echo "=========================================="
echo "CyanBase 青渊底座 - 一键打包部署"
echo "=========================================="
echo ""

# 项目路径
PROJECT_DIR="/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend"
JAR_NAME="cyanbase-backend-1.0.0.jar"
LOG_DIR="${PROJECT_DIR}/logs"

cd ${PROJECT_DIR}

# 1. 停止旧进程
echo "【1】停止旧进程..."
if [ -f "stop.sh" ]; then
    bash stop.sh
else
    PID=$(ps aux | grep ${JAR_NAME} | grep -v grep | awk '{print $2}')
    if [ -n "$PID" ]; then
        echo "发现运行中的进程: $PID，正在停止..."
        kill $PID
        sleep 5
        # 如果还在运行，强制杀死
        if ps -p $PID > /dev/null; then
            echo "正常停止失败，强制杀死进程..."
            kill -9 $PID
        fi
        echo "进程已停止"
    else
        echo "未发现运行中的进程"
    fi
fi
echo ""

# 2. Maven打包
echo "【2】Maven打包..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ 打包失败！"
    exit 1
fi
echo "✅ 打包成功"
echo ""

# 3. 创建日志目录
echo "【3】创建日志目录..."
mkdir -p ${LOG_DIR}
echo "✅ 日志目录就绪"
echo ""

# 4. 启动新进程
echo "【4】启动新进程..."
if [ -f "start.sh" ]; then
    bash start.sh
else
    nohup java -jar target/${JAR_NAME} > ${LOG_DIR}/startup.log 2>&1 &
    echo $! > ${LOG_DIR}/app.pid
    echo "✅ 项目已启动，PID: $!"
fi
echo ""

# 5. 等待启动
echo "【5】等待服务启动..."
sleep 15

# 6. 检查启动状态
echo "【6】检查启动状态..."
if ps aux | grep ${JAR_NAME} | grep -v grep > /dev/null; then
    echo "✅ 服务启动成功！"
    echo "日志文件: ${LOG_DIR}/cyanbase.log"
    echo "查看日志: tail -f ${LOG_DIR}/cyanbase.log"
else
    echo "❌ 服务启动失败！请查看日志: ${LOG_DIR}/startup.log"
    exit 1
fi

echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="