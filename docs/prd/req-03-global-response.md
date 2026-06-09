# REQ-03 通用返回体 Result + 异常处理

## 产品定位

统一后端 API 的返回格式和异常处理机制，确保前端收到一致的结构化响应。

## 用户故事

**作为** 前端开发者
**我想要** 所有 API 返回统一格式的 JSON 结构（含 code、message、data）
**以便于** 统一处理成功/失败场景

## 已有实现

- `Result<T>` — 统一返回体（code + message + data）
- `ResultCode` — 错误码枚举（SUCCESS/FAIL/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/INTERNAL_ERROR）
- `BusinessException` — 业务异常

## 待实现

### GlobalExceptionHandler

在各端模块（app / admin）中新增全局异常处理器：

- 捕获 `BusinessException` → `Result.fail(code, message)`
- 捕获 `MethodArgumentNotValidException`（参数校验） → `Result.fail(400, 校验错误信息)`
- 捕获其他 `Exception` → `Result.fail(500, "服务器内部错误")`

### 补充 ResultCode

当前 `ResultCode` 已包含 401/403，无需额外补充。

## 验收标准

- [ ] app 模块有 GlobalExceptionHandler
- [ ] admin 模块有 GlobalExceptionHandler
- [ ] BusinessException 返回对应 code + message
- [ ] 参数校验失败返回 400 + 具体字段错误
- [ ] 未知异常返回 500，不暴露堆栈

## 技术决策

| 决策项 | 选择 |
|--------|------|
| 异常处理器位置 | 各端模块的 common 包下（依赖 Spring Web，不在 core） |
| 参数校验错误格式 | 拼接所有字段错误信息为一条 message |
