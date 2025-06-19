# Deep Search 前端代码部署

## 环境准备

1. 安装 Node.js，Node 版本不小于16.18.1
2. 安装 pnpm
    ```bash
    npm install -g pnpm
    ```

## 操作步骤

1. 安装依赖
    ```bash
    pnpm install
    ```
2. (可选) 默认访问本地启动的后端服务。也可设置环境变量，访问线上已有 Bot API
    ```bash
    export ARK_API_KEY=xxx
    export DOMAIN=https://ark.cn-beijing.volces.com
    export BOT_ID=bot-xxxxx-xxxx
    ```
3. 启动前端服务
    ```bash
    pnpm dev
    ```
4. 访问前端页面
    ```bash
    http://localhost:8080/
    ```
