# REQ-01 后端项目骨架（Spring Boot + DDD 目录结构）

## 产品定位

Knowledge Game — 知识记忆卡牌游戏。技术栈：Spring Boot 3.x (JDK 21) + MySQL + Spring Data JPA + JWT + React + TypeScript + Ant Design Pro。

## 用户故事

**作为** 开发者
**我想要** 一个从零搭建好的后端项目骨架（DDD 六边形目录结构 + 通用组件 + 全链路示例）
**以便于** 后续各功能模块可以在统一的架构规范下开发

## 验收标准

- [ ] Spring Boot 3.x 项目初始化（JDK 21），pom.xml 包含所有必要依赖
- [ ] DDD 六边形目录结构（api/application/domain/infrastructure/config/common）全部创建
- [ ] 启动类 KnowledgeGameApplication.java 可正常启动
- [ ] application.yml 配置好数据源、端口（8080）、Spring Data JPA
- [ ] 统一返回体 Result<T> + ResultCode 枚举
- [ ] BusinessException + GlobalExceptionHandler
- [ ] 全链路示例（User CRUD）展示 DDD 各层协作：Controller → AppService → DomainService → RepositoryPort → RepositoryAdapter → JpaRepository
- [ ] .gitignore 配置完整
- [ ] CORS 跨域配置完成

## 技术决策

| 决策项 | 选择 |
|--------|------|
| Spring Boot | 3.x (JDK 21) |
| 构建 | Maven |
| ORM | Spring Data JPA |
| 端口 | 8080 |
| 数据库 | MySQL（本地，建表留到具体功能需求） |
