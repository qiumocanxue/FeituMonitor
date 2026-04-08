# FeituMonitor 文件模块重构

## 1. 远程设备文件管理 (`com.feitu.monitor.remote`)

**职责**：负责与远程 PC/Linux 设备的实时 WebSocket 交互，处理指令发送与执行。

- **`AgentFileActivity.kt`**：远程文件浏览器主界面，处理盘符切换和路径跳转。
- **`FileAdapter.kt`**：用于显示远程设备文件列表的适配器。
- **`models/FileCommand.kt`**：定义手机端发往远程代理端的 WebSocket 指令（如 LIST, DELETE, OPEN）。
- **`models/RemoteFile.kt`**：远程端返回的文件/文件夹基础实体模型。

## 2. 云端文件中心 (`com.feitu.monitor.cloud`)

**职责**：负责中心服务器（类似 FTP/私有云盘）的 HTTP 文件浏览与在线预览。

- **`FileCenterFragment.kt`**：文件中心主界面，负责解析服务器 HTML 并展示列表。
- **`FilePreviewActivity.kt`**：在线查看文本类文件的预览界面。
- **`models/FtpModels.kt`**：云端文件实体类（FtpItem）及相关配置。

## 3. 通用下载引擎 (`com.feitu.monitor.download`)

**职责**：提供全应用通用的多线程下载、断点续传及进度管理功能，服务于上述两个业务模块。

- **`DownloadListActivity.kt`**：下载任务管理界面，支持进度实时刷新与任务操作。
- **`models/FtpDownloadManager.kt`**：下载引擎的核心单例，处理具体的协程下载逻辑、HTTP Range 请求及状态持久化。
- **`models/DownloadTask.kt`**：包含在 `FtpDownloadManager.kt` 中，定义下载任务的状态、进度和速度。

## 4. 公共基建工具 (`com.feitu.monitor.common`)

**职责**：存放跨模块调用的底层协议和工具类。

- **`utils/FileBase64Utils.kt`**：提供文件与 Base64 字符串的互相转换，常用于 WebSocket 传输小文件。
- **`models/MessageEnvelope.kt`**：WebSocket 通信的通用消息信封协议。

------

## 重构建议步骤

1. **创建文件夹**：在 Android Studio 的 `java/com.feitu.monitor` 下新建 `remote`, `cloud`, `download`, `common` 四个 Package。
2. **移动文件**：按照上述列表拖动文件。Android Studio 会自动更新所有文件的 `package` 声明和 `import` 引用。
3. **提取 Adapter**：建议将 `AgentFileActivity` 和 `DownloadListActivity` 中的内部类 Adapter 提取出来成为独立的 `.kt` 文件，放在各自的功能包下。
4. **统一模型**：未来可考虑抽象出一个通用的 `BaseFileItem` 接口，让远程文件和云端文件在 UI 展示上实现统一。