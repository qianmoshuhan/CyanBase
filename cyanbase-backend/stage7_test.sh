#!/bin/bash
# 阶段七功能测试脚本

BASE_URL="http://localhost:8090"

echo "=========================================="
echo "CyanBase 阶段七功能测试"
echo "=========================================="
echo ""

# 1. 登录获取Token
echo "【1】登录获取Token..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "❌ 登录失败！"
    echo "响应: $LOGIN_RESPONSE"
    exit 1
fi

echo "✅ 登录成功，Token: ${TOKEN:0:20}..."
echo ""

# 2. 创建表单定义
echo "【2】创建表单定义..."
FORM_CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/form/definition" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "formKey": "test_form_'$(date +%s)'",
    "formName": "测试表单-'$(date +%s)'",
    "description": "自动化测试表单",
    "category": "测试",
    "version": 1,
    "status": 0
  }')

echo "响应: $FORM_CREATE_RESPONSE"
FORM_ID=$(echo $FORM_CREATE_RESPONSE | grep -o '"code":[0-9]*' | grep -o '[0-9]*$')

if [ "$FORM_ID" = "200" ]; then
    echo "✅ 表单创建成功"
else
    echo "⚠️  表单可能已存在或创建失败"
fi
echo ""

# 3. 查询表单列表
echo "【3】查询表单列表..."
curl -s -X GET "$BASE_URL/form/definition/list?pageNum=1&pageSize=5" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool 2>/dev/null || echo "（JSON格式化失败）"
echo ""

# 4. 创建流程定义
echo "【4】创建流程定义..."
WORKFLOW_CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/workflow/definition" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "processKey": "test_workflow_'$(date +%s)'",
    "processName": "测试流程-'$(date +%s)'",
    "description": "自动化测试流程",
    "category": "测试",
    "version": 1,
    "status": 0
  }')

echo "响应: $WORKFLOW_CREATE_RESPONSE"
WORKFLOW_CODE=$(echo $WORKFLOW_CREATE_RESPONSE | grep -o '"code":[0-9]*' | grep -o '[0-9]*$')

if [ "$WORKFLOW_CODE" = "200" ]; then
    echo "✅ 流程创建成功"
else
    echo "⚠️  流程可能已存在或创建失败"
fi
echo ""

# 5. 查询流程列表
echo "【5】查询流程列表..."
curl -s -X GET "$BASE_URL/workflow/definition/list?pageNum=1&pageSize=5" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool 2>/dev/null || echo "（JSON格式化失败）"
echo ""

echo "=========================================="
echo "测试完成！"
echo "=========================================="