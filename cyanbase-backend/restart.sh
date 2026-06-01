#!/bin/bash
# ========================================
# CyanBase 项目重启脚本（自动编译打包）
# 用法: ./restart.sh
# ========================================

set -e

echo "=========================================="
echo "CyanBase 青渊底座 - 重启服务"
echo "=========================================="
echo ""

PROJECT_DIR="/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend"
cd ${PROJECT_DIR}

# 1. 停止服务
echo "【1】停止服务..."
if [ -f "stop.sh" ]; then
    bash stop.sh
else
    echo "❌ stop.sh 不存在"
    exit 1
fi
echo ""

# 2. 等待进程释放
echo "【2】等待进程完全释放..."
sleep 3
echo ""

# 3. 编译 + 打包
echo "【3】编译打包最新代码..."
mvn clean package -DskipTests
echo "✅ 编译打包完成"
echo ""

# 4. 启动服务
echo "【4】启动服务..."
if [ -f "start.sh" ]; then
    bash start.sh
else
    echo "❌ start.sh 不存在"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ 重启完成！"
echo "=========================================="
