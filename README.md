# CyanBase

> 
> 面向政企场景的多租户 SaaS 底座，基于 SpringBoot3 + Vue3 构建，内置三层权限漏斗模型与轻量化工作流引擎，开箱即可快速搭建业务系统。

## ✨ 核心特性

- **三层权限校验 PermissionGuard**
L1 租户隔离 → L2 数据范围权限 → L3 RBAC 按钮权限，逐层漏斗校验，支持 SpEL 表达式、自定义数据切面、权限跳过注解。
- **原生多租户**
租户独立菜单、租户登录体系、租户维度数据隔离，适配一套实例多单位部署。
- **内置工作流引擎**
表单与流程绑定、会签 / 或签、流程超时任务、BPMN 生成、待办 / 已办 / 抄送能力，支持业务自定义绑定器扩展。
- **表单设计器**
可视化拖拽表单，表单数据与流程实例联动。
- **基础管理能力**
用户、角色、部门、菜单、字典、参数、操作日志、登录日志、定时任务、API 监控。
- **业务模块：三大（三重一大）**
配套三重一大业务域，含事项、议题、会议、执行全链路管理，适配国资监管场景。

## 🛠️ 技术栈

后端：Spring Boot 3 | MyBatis-Plus | Redis | Spring Security
前端：Vue3 | Vite | Element Plus | bpmn-js
数据库：达梦 DM8 / MariaDB / MySQL

## 📦 目录说明

```
cyanbase-backend    // 后端服务
cyanbase-frontend   // Vue3前端
docs                // 设计文档、权限、工作流、表单说明
```

## 🚀 快速启动

```
# 后端
mvn clean package
sh start.sh

# 前端
npm install
npm run dev
```

## 📄 说明

本项目为自研数字底座，主打权限底座复用，可在此之上快速孵化各类政企业务应用。
