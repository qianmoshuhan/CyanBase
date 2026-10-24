#!/bin/bash
PROJECT_DIR="/home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend"
LOG_DIR="${PROJECT_DIR}/logs"
PID_FILE="${LOG_DIR}/app.pid"

if [ -f "$PID_FILE" ];then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null;then
        echo "正在停止服务 PID: $PID"
        kill $PID
        sleep 3
        if ps -p $PID > /dev/null;then
            echo "进程未退出，强制kill"
            kill -9 $PID
        fi
    fi
    rm -f $PID_FILE
fi
echo "服务已停止"

