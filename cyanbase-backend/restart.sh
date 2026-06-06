#!/bin/bash
# ========================================
# CyanBase 项目重启脚本（独立版，不依赖启停脚本）
# 用法: ./restart.sh
# ========================================

set -e

echo "=========================================="
echo "CyanBase 青渊底座 - 重启服务（独立版）"
echo "=========================================="
echo ""

# 配置项（统一新路径）
PROJECT_DIR="/home/admin/CyanBase/cyanbase-backend"
JAR_NAME="cyanbase-backend-1.0.0.jar"
LOG_DIR="${PROJECT_DIR}/logs"
PID_FILE="${LOG_DIR}/app.pid"

# 切换到项目目录
cd ${PROJECT_DIR} || { echo "❌ 项目目录不存在: ${PROJECT_DIR}"; exit 1; }

# ======================
# 【1】停止现有服务
# ======================
echo "【1】停止运行中的服务..."
# 初始化PID变量
PID=""

# 尝试从PID文件获取进程ID
if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null; then
        echo "发现PID文件中的运行进程，PID: $PID"
        echo "正在正常停止..."
        kill $PID
        
        # 等待进程结束（最多30秒）
        for i in {1..30}; do
            if ! ps -p $PID > /dev/null; then
                echo "✅ 服务已正常停止"
                rm -f $PID_FILE
                PID=""
                break
            fi
            sleep 1
        done
        
        # 超时则强制杀死
        if [ -n "$PID" ] && ps -p $PID > /dev/null; then
            echo "⚠️  正常停止超时，强制杀死进程..."
            kill -9 $PID
            echo "✅ 服务已强制停止"
            rm -f $PID_FILE
            PID=""
        fi
    else
        echo "⚠️  PID文件存在但进程不存在，清理过期PID文件"
        rm -f $PID_FILE
    fi
fi

# 如果PID文件未找到进程，通过进程名查找
if [ -z "$PID" ]; then
    PID=$(ps aux | grep ${JAR_NAME} | grep -v grep | awk '{print $2}')
    if [ -n "$PID" ]; then
        echo "通过进程名发现运行进程，PID: $PID"
        echo "正在停止..."
        kill $PID
        sleep 5
        
        # 检查是否停止成功，失败则强制杀死
        if ps -p $PID > /dev/null; then
            echo "⚠️  正常停止失败，强制杀死进程..."
            kill -9 $PID
        fi
        echo "✅ 服务已停止"
        PID=""
    else
        echo "⚠️  未发现运行中的服务进程"
    fi
fi
echo ""

# ======================
# 【2】编译打包项目
# ======================
echo "【2】开始编译打包最新代码..."
# 检查mvn命令是否存在
if ! command -v mvn &> /dev/null; then
    echo "❌ 未找到Maven命令，请确保已安装并配置环境变量"
    exit 1
fi

# 执行编译打包（跳过测试）
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ 编译打包失败，请检查代码或Maven配置"
    exit 1
fi
echo "✅ 编译打包完成"
echo ""

# ======================
# 【3】检查JAR文件
# ======================
echo "【3】检查打包产物..."
JAR_PATH="${PROJECT_DIR}/target/${JAR_NAME}"
if [ ! -f "$JAR_PATH" ]; then
    echo "❌ JAR文件不存在: ${JAR_PATH}"
    exit 1
fi
echo "✅ JAR文件存在: ${JAR_PATH}"
echo ""

# ======================
# 【4】启动服务
# ======================
echo "【4】启动服务..."
# 创建日志目录
mkdir -p ${LOG_DIR}

# 启动服务（后台运行，输出日志到startup.log）
nohup java -jar ${JAR_PATH} > ${LOG_DIR}/startup.log 2>&1 &
NEW_PID=$!
echo $NEW_PID > $PID_FILE

echo "✅ 服务启动中，PID: $NEW_PID"
echo "日志文件: ${LOG_DIR}/startup.log"
echo "应用日志: ${LOG_DIR}/cyanbase.log"
echo "查看日志命令: tail -f ${LOG_DIR}/cyanbase.log"
echo ""

# ======================
# 【5】检查启动状态
# ======================
echo "【5】等待服务初始化并检查状态..."
sleep 10

if ps -p $NEW_PID > /dev/null; then
    echo "✅ 服务重启成功，运行正常！"
else
    echo "❌ 服务启动失败！请查看日志: ${LOG_DIR}/startup.log"
    rm -f $PID_FILE
    exit 1
fi

echo ""
echo "=========================================="
echo "🎉 重启流程全部完成！"
echo "=========================================="
