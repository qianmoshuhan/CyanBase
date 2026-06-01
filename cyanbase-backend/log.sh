#!/bin/bash
# ========================================
# CyanBase 实时查看日志脚本
# 用法: ./log.sh [lines]
# 参数: lines - 显示最后多少行，默认100行
# ========================================

PROJECT_DIR="/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend"
LOG_FILE="${PROJECT_DIR}/logs/cyanbase.log"

# 检查日志文件是否存在
if [ ! -f "$LOG_FILE" ]; then
    echo "❌ 日志文件不存在: $LOG_FILE"
    echo "请先启动服务生成日志"
    exit 1
fi

# 获取显示行数，默认100
LINES=${1:-100}

echo "=========================================="
echo "CyanBase 青渊底座 - 实时日志监控"
echo "日志文件: $LOG_FILE"
echo "显示最后 ${LINES} 行"
echo "按 Ctrl+C 退出"
echo "=========================================="
echo ""

tail -n ${LINES} -f $LOG_FILE