# LoadRunner 12 录制配置清单

> **目标**：录制时只保留页面调用的 **HTTP 接口请求**，自动过滤 html / js / css / 图片 / 字体等静态资源，避免脚本冗余。
>
> **适用**：BOSS 类前后端分离系统（AJAX/XHR 请求多）。

---

## 一、快速配置总览

| 配置项 | 建议值 | 说明 |
|--------|--------|------|
| 脚本模式 | **URL-based script** | 保证 AJAX/接口请求全部录到 |
| 过滤器 | **Non-Resources / URL 过滤** | 排除静态资源扩展名 |
| 路径过滤 | 排除 `/static/`、`/assets/` 等 | 应对无扩展名的静态资源 |
| 白名单 | 只保留 `/order/`、`/upc/`、`/svc/` | 最精准，直接对应业务接口前缀 |
| 录制日志 | **勾选 Record and save as .lrr** | 便于核对过滤效果 |
| 录制目标 | 录到 **Action** | 业务操作主体 |

---

## 二、详细配置步骤

### 步骤 1：新建脚本选协议

| 被测系统 | 协议 |
|----------|------|
| 普通 Web 系统（BOSS 前台） | **Web - HTTP/HTML** |
| REST / SOAP 接口 | Web Services |

### 步骤 2：打开录制选项

```
开始录制对话框 → Recording Options... → HTTP Properties → Advanced
```

### 步骤 3：设置脚本模式

选择 **URL-based script**（记录每个独立请求，含所有 XHR）。

> 传统页面可选用 HTML-based script（脚本更简洁）。

### 步骤 4：配置 Non-Resource 过滤器

在过滤器设置处，把静态资源扩展名加入 **Exclude（排除）** 列表，规则见「第三章」。

### 步骤 5：录到 Action

```
Record into Action → Action
```

### 步骤 6：勾选录制日志

勾选 **Record and save as .lrr**，录制后可打开 Record Log 查看被拦截的 URL。

---

## 三、过滤规则清单（可直接复制）

### 3.1 排除扩展名（Exclude）

```
*.css
*.js
*.gif
*.jpg
*.jpeg
*.png
*.bmp
*.ico
*.svg
*.webp
*.woff
*.woff2
*.ttf
*.eot
*.map
*.mp3
*.mp4
*.swf
```

### 3.2 排除路径关键字（Exclude）

适用于 URL 中**不含扩展名**的静态资源：

```
/static/
/assets/
/images/
/favicon.ico
/portal/
```

### 3.3 业务接口白名单（Include，可选，最精准）

只保留业务前缀，直接对应 BOSS 接口路径：

```
/order/
/upc/
/svc/
```

> **推荐策略**：优先用 **3.3 白名单**（最干净），白名单不覆盖时再叠加 **3.1 + 3.2 排除**。

---

## 四、录制后脚本清理检查清单

录制完成后，检查 VuGen 脚本是否还残留静态资源调用：

- [ ] 搜索 `web_image`，如有则删除
- [ ] 搜索 `.css`，删除对应 `web_url`
- [ ] 搜索 `.js`，删除对应 `web_url`
- [ ] 搜索 `.png` / `.jpg` / `.gif`，删除对应调用
- [ ] 确认所有业务接口请求（`/order/`、`/upc/`、`/svc/`）均已录制
- [ ] 确认登录 / token 相关请求完整保留
- [ ] 脚本可正常回放（Replay，`Ctrl+F5`）

**清理操作**：选中多余行 → 右键 **Delete**（删除）或 **Comment**（注释）。

---

## 五、回放过滤头文件（可选，加快回放）

新建 `globle_filter.h`，在 `vuser_init` 首行引入（`#include "globle_filter.h"`）或直接放置：

```c
/* 回放时排除静态资源下载，加快回放速度 */
web_add_filter("URL=*.css",   "Action=Exclude");
web_add_filter("URL=*.js",    "Action=Exclude");
web_add_filter("URL=*.png",   "Action=Exclude");
web_add_filter("URL=*.jpg",   "Action=Exclude");
web_add_filter("URL=*.jpeg",  "Action=Exclude");
web_add_filter("URL=*.gif",   "Action=Exclude");
web_add_filter("URL=*.ico",   "Action=Exclude");
web_add_filter("URL=*.woff",  "Action=Exclude");
web_add_filter("URL=*.woff2", "Action=Exclude");
web_add_filter("URL=*.ttf",   "Action=Exclude");
web_add_filter("URL=*.svg",   "Action=Exclude");
```

> 注意：`web_add_filter` 仅影响**回放**阶段的资源下载，不影响录制。

---

## 六、验证清单

- [ ] 录制时左下角**已录请求数**明显减少
- [ ] 打开 **Record Log（.lrr）**，确认静态资源被 Exclusion 规则拦截
- [ ] 查看 **Generation Log**，确认生成脚本时跳过了资源请求
- [ ] 脚本中不再出现 `web_image`、`*.css`、`*.js` 的 `web_url`
- [ ] 回放成功，业务接口返回正常

---

## 七、常见问题排查

| 现象 | 原因 | 解决 |
|------|------|------|
| 接口请求也没录到 | 过滤规则过宽（如 Exclude `/api/`） | 收窄规则，或改用 Include 白名单 |
| 静态资源仍录进来 | URL 无扩展名 | 用路径关键字 `/static/` 过滤 |
| 过滤设置换脚本后丢失 | 过滤器随脚本保存 | 在 Recording Options 配置后确认脚本已保存 |
| `web_add_filter` 不生效 | 写法/位置不对 | 放在 `vuser_init` 最前 |
| Chrome 录不了 | 浏览器版本不兼容 | 用 LR 支持的版本，或改用 IE/Firefox |

---

## 八、一键速记

```
录制模式:  URL-based script
Exclude:   *.css *.js *.gif *.jpg *.jpeg *.png *.ico *.bmp
           *.svg *.webp *.woff *.woff2 *.ttf *.eot *.map
Exclude路径: /static/ /assets/ /images/ /favicon.ico
Include:   /order/ /upc/ /svc/       （白名单，最精准）
录制日志:  勾选 .lrr
录到:      Action
```