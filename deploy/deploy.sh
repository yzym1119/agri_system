#!/bin/bash
set -e

echo "========================================="
echo "  农资管理系统 - 生产部署脚本"
echo "========================================="

COMPOSE="docker compose -f deploy/docker-compose.prod.yml"

# 健康探测目标：生产域名（正确 SNI + 证书校验）。可用环境变量覆盖：
#   DEPLOY_PROBE_URL=https://admin.example.com/health/ready ./deploy/deploy.sh
# 不再使用 -k 跳过 TLS 校验——跳过校验会把「证书错误/被劫持」误判为健康。
DOMAIN="${DEPLOY_DOMAIN:-admin.yourdomain.com}"
PROBE_URL="${DEPLOY_PROBE_URL:-https://${DOMAIN}/health/ready}"

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
$COMPOSE build

echo ""
echo "🚀 启动服务..."
$COMPOSE up -d

# 容器运行/健康状态检查：mysql、redis、backend、client、nginx 任一非 healthy 即失败
check_containers() {
    local failed=0
    for svc in mysql redis backend client nginx; do
        local state health
        state=$($COMPOSE ps --format '{{.Name}} {{.State}}' 2>/dev/null | awk -v s="agri-$svc" '$1==s {print $2}')
        health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "agri-$svc" 2>/dev/null || echo "missing")
        if [ "$state" != "running" ] || { [ "$health" != "healthy" ] && [ "$health" != "no-healthcheck" ]; }; then
            echo "❌ 容器 agri-$svc 状态异常: state=$state health=$health"
            failed=1
        fi
    done
    return $failed
}

# HTTPS 实际业务入口探测（就绪级）：nginx /health/ready → backend /health/ready（含数据库与Redis依赖探测）。
# 1) 不能用 http:// 探测——HTTP 入口 301 跳转 HTTPS，curl -f 不把 301 当错误，会误报成功；
# 2) 不能用 -k 跳过证书校验——证书过期/域名不匹配/被劫持都会被误判为健康；
#    探测域名必须与 server_name 一致以获得正确 SNI，证书校验失败即发布失败。
# 3) 探测就绪端点而非存活端点——/health 只证明进程活着，/health/ready 才证明 DB/Redis 可用。
check_https_entrypoint() {
    local body status
    body=$(curl -sf --proto '=https' --tlsv1.2 -m 5 -w '\n%{http_code}' "$PROBE_URL" 2>/dev/null) || return 1
    status=$(echo "$body" | tail -n1)
    [ "$status" = "200" ] && echo "$body" | head -n -1 | grep -q '"status":"UP"'
}

echo ""
echo "⏳ 等待服务就绪（HTTPS 业务入口 + 容器健康状态）..."
ok=""
for i in $(seq 1 60); do
    if check_https_entrypoint && check_containers; then
        ok=1
        break
    fi
    sleep 3
done

if [ -z "$ok" ]; then
    echo "❌ 服务启动超时或依赖不健康，尝试自动恢复（重启一次）..."
    $COMPOSE restart backend client nginx
    sleep 15
    if check_https_entrypoint && check_containers; then
        echo "✅ 自动恢复成功。"
        exit 0
    fi
    echo "❌ 恢复失败，发布未通过健康验收。请执行以下命令诊断："
    echo "   $COMPOSE ps"
    echo "   $COMPOSE logs backend client"
    echo "   curl -s $PROBE_URL"
    echo "   （如需回滚：用上一版镜像/代码重新构建部署，本脚本不自动降级）"
    exit 1
fi

echo "✅ 服务启动成功（HTTPS 入口 200 且依赖健康，全部容器 healthy）！"
echo ""
echo "访问地址: https://$DOMAIN（探测入口: $PROBE_URL）"
exit 0
