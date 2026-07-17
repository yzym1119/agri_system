#!/bin/bash
set -e

echo "========================================="
echo "  农资管理系统 - 生产部署脚本"
echo "========================================="

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "❌ 缺少 .env 文件，请复制 deploy/.env.example 为 .env 并填写配置"
    exit 1
fi

# 检查 SSL 证书
if [ ! -f deploy/ssl/fullchain.pem ] || [ ! -f deploy/ssl/privkey.pem ]; then
    echo "⚠️  SSL 证书未配置（deploy/ssl/）"
    echo "   请先获取 SSL 证书（Let's Encrypt 或其他 CA）"
    echo "   如果暂时不需要 HTTPS，请手动修改 nginx.conf"
    exit 1
fi

# 构建并启动
echo ""
echo "🔨 构建 Docker 镜像..."
docker compose -f deploy/docker-compose.prod.yml build

echo ""
echo "🚀 启动服务..."
docker compose -f deploy/docker-compose.prod.yml up -d

echo ""
echo "⏳ 等待服务就绪..."
for i in $(seq 1 30); do
    if curl -sf http://localhost/health > /dev/null 2>&1; then
        echo "✅ 服务启动成功！"
        echo ""
        echo "访问地址: https://admin.yourdomain.com"
        exit 0
    fi
    sleep 2
done

echo "❌ 服务启动超时，请检查日志："
echo "   docker compose -f deploy/docker-compose.prod.yml logs"
exit 1
