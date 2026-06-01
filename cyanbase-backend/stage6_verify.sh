#!/bin/bash
# CyanBase 阶段六 - 快速验证脚本

echo "======================================"
echo "CyanBase 青渊底座 - 阶段六 验证脚本"
echo "======================================"
echo ""

# 1. 检查数据库表
echo "1. 检查数据库表..."
mysql -h 120.48.47.253 -P 3306 -u petclinic -pxJgh2021@xzd cyan_base -e "
SELECT 
    TABLE_NAME as '表名',
    TABLE_COMMENT as '表说明'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'cyan_base' 
AND TABLE_NAME IN ('sys_job', 'sys_job_log', 'sys_message', 'sys_message_user')
ORDER BY TABLE_NAME;
"

echo ""
echo "2. 检查定时任务初始化数据..."
mysql -h 120.48.47.253 -P 3306 -u petclinic -pxJgh2021@xzd cyan_base -e "
SELECT id, job_name, cron_expression, status as '状态(0正常1暂停)' 
FROM sys_job 
ORDER BY id;
"

echo ""
echo "3. 检查消息类型字典数据..."
mysql -h 120.48.47.253 -P 3306 -u petclinic -pxJgh2021@xzd cyan_base -e "
SELECT dict_type_name, dict_type_code 
FROM sys_dict_type 
WHERE dict_type_code = 'sys_message_type';
"

echo ""
echo "4. 统计新增代码文件..."
echo "   Job模块文件:"
find /home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend/src/main/java/com/cyan/modules/job -name "*.java" | wc -l
echo "   Message模块文件:"
find /home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend/src/main/java/com/cyan/modules/message -name "*.java" | wc -l
echo "   Common/Job组件文件:"
find /home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend/src/main/java/com/cyan/common/job -name "*.java" | wc -l
echo "   Common/Message组件文件:"
find /home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend/src/main/java/com/cyan/common/message -name "*.java" | wc -l

echo ""
echo "5. 检查编译状态..."
cd /home/xjgh2025XZD/Cyan/CyanBase/cyanbase-backend
if mvn clean compile -DskipTests > /tmp/compile.log 2>&1; then
    echo "   ✅ 编译成功"
else
    echo "   ❌ 编译失败，请查看日志: /tmp/compile.log"
fi

echo ""
echo "======================================"
echo "验证完成！"
echo "======================================"
echo ""
echo "下一步操作："
echo "1. 启动项目: mvn spring-boot:run"
echo "2. 查看接口文档: stage6_api_test.md"
echo "3. 查看完成报告: stage6_completion_report.md"
