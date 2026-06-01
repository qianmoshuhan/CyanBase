# CyanBase 青渊底座 - 阶段六 接口测试指南

## 前置条件
1. 确保项目已启动
2. 确保已登录并获取JWT Token
3. 将 `{token}` 替换为实际的JWT Token

## 一、定时任务接口测试

### 1. 查询任务列表
```bash
curl -X GET "http://localhost:8080/system/job/list?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer {token}"
```

### 2. 获取任务详情
```bash
curl -X GET "http://localhost:8080/system/job/1" \
  -H "Authorization: Bearer {token}"
```

### 3. 新增任务
```bash
curl -X POST "http://localhost:8080/system/job" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "测试任务",
    "jobGroup": "TEST",
    "invokeTarget": "sysJobTask.cleanOperationLog",
    "cronExpression": "0 0 5 * * ?",
    "misfirePolicy": 1,
    "concurrent": 0,
    "status": 0,
    "remark": "这是一个测试任务"
  }'
```

### 4. 修改任务
```bash
curl -X PUT "http://localhost:8080/system/job" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "id": 7,
    "jobName": "测试任务-修改",
    "jobGroup": "TEST",
    "invokeTarget": "sysJobTask.cleanOperationLog",
    "cronExpression": "0 0 6 * * ?",
    "misfirePolicy": 1,
    "concurrent": 0,
    "status": 0,
    "remark": "修改后的测试任务"
  }'
```

### 5. 删除任务
```bash
curl -X DELETE "http://localhost:8080/system/job/7" \
  -H "Authorization: Bearer {token}"
```

### 6. 暂停任务
```bash
curl -X PUT "http://localhost:8080/system/job/pause/1" \
  -H "Authorization: Bearer {token}"
```

### 7. 恢复任务
```bash
curl -X PUT "http://localhost:8080/system/job/resume/1" \
  -H "Authorization: Bearer {token}"
```

### 8. 立即执行一次
```bash
curl -X PUT "http://localhost:8080/system/job/execute/1" \
  -H "Authorization: Bearer {token}"
```

### 9. 查询任务执行日志
```bash
curl -X GET "http://localhost:8080/system/job/log/list?pageNum=1&pageSize=10&jobId=1" \
  -H "Authorization: Bearer {token}"
```

## 二、消息通知接口测试

### 1. 查询我的消息列表
```bash
curl -X GET "http://localhost:8080/system/message/list?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer {token}"
```

### 2. 查询指定类型的消息
```bash
curl -X GET "http://localhost:8080/system/message/list?pageNum=1&pageSize=10&messageType=1" \
  -H "Authorization: Bearer {token}"
```

### 3. 发送全员消息
```bash
curl -X POST "http://localhost:8080/system/message/sendAll" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "系统维护通知",
    "content": "系统将于今晚22:00进行维护，预计持续1小时",
    "messageType": 1
  }'
```

### 4. 发送指定用户消息
```bash
curl -X POST "http://localhost:8080/system/message/sendUser" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "个人通知",
    "content": "您的账户信息已更新",
    "messageType": 2,
    "userIds": [1, 2, 3]
  }'
```

### 5. 发送指定角色消息
```bash
curl -X POST "http://localhost:8080/system/message/sendRole" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "管理员通知",
    "content": "请及时处理待审核事项",
    "messageType": 2,
    "roleIds": [1]
  }'
```

### 6. 标记消息已读
```bash
curl -X PUT "http://localhost:8080/system/message/read/1" \
  -H "Authorization: Bearer {token}"
```

### 7. 全部标记已读
```bash
curl -X PUT "http://localhost:8080/system/message/readAll" \
  -H "Authorization: Bearer {token}"
```

### 8. 删除消息
```bash
curl -X DELETE "http://localhost:8080/system/message/1" \
  -H "Authorization: Bearer {token}"
```

### 9. 获取未读消息数量
```bash
curl -X GET "http://localhost:8080/system/message/unread/count" \
  -H "Authorization: Bearer {token}"
```

## 三、系统自动化任务说明

以下任务已配置为自动执行，无需手动调用：

1. **清理7天前操作日志** - 每天凌晨2点执行
2. **清理7天前登录日志** - 每天凌晨2点执行
3. **清理30天前临时文件** - 每天凌晨3点执行
4. **清理过期Redis缓存** - 每天凌晨4点执行
5. **每日用户登录统计** - 每天凌晨0点执行
6. **同步权限数据到Redis** - 每30分钟执行

可以通过"立即执行一次"接口手动触发这些任务进行测试。

## 四、消息自动推送场景

以下场景会自动触发消息推送：

1. **用户登录成功** - 在登录成功后调用 `MessagePushService.pushLoginSuccess()`
2. **密码错误** - 在密码验证失败时调用 `MessagePushService.pushPasswordError()`
3. **文件上传完成** - 在文件上传成功后调用 `MessagePushService.pushFileUploadComplete()`
4. **定时任务执行失败** - 在任务执行异常时调用 `MessagePushService.pushJobExecuteFailed()`
5. **新用户注册** - 在用户注册成功后调用 `MessagePushService.pushWelcomeMessage()`

需要在相应的业务代码中集成这些调用。

## 五、注意事项

1. 所有接口都需要JWT Token鉴权
2. Cron表达式格式：秒 分 时 日 月 周
3. 消息类型：1-系统公告 2-操作通知 3-预警提醒 4-欢迎消息
4. 发送方式：1-全员 2-指定用户 3-指定角色
5. 任务状态：0-正常 1-暂停
6. 消息已读状态：0-未读 1-已读
