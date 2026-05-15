#!/usr/bin/env bash
# GZLY 生产 nginx access_gzly.log 延迟分析（R5：nginx 项目分析脚本指标）。
#
# 用法：
#   bash analyze_nginx_latency.sh                    # 默认 /var/log/nginx/access_gzly.log
#   bash analyze_nginx_latency.sh /var/log/nginx/access_gzly.log
#   bash analyze_nginx_latency.sh log path/segment   # 第 2 个参数：仅统计 URI 包含子串的行
#
# 输出：
#   - 总请求数 / 状态码分布
#   - request_time / upstream_response_time 的 P50 / P95 / P99 / max
#   - 按 path 前缀（/api/volunteer, /api/admin, /api/ml, /api/site-stats, 其它）分桶聚合
#
# 不修改任何日志文件，纯只读统计。日志格式来自 nginx.conf 的 `log_format gzly_main`：
#   $remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent
#   $request_time $upstream_response_time "$http_referer" "$http_user_agent" "$http_x_forwarded_for"
#
# 退出码：
#   0 - 正常输出
#   2 - 输入文件不存在 / 不可读
set -euo pipefail

LOG_FILE="${1:-/var/log/nginx/access_gzly.log}"
PATH_FILTER="${2:-}"

if [[ ! -r "$LOG_FILE" ]]; then
  echo "[analyze][ERROR] 日志文件不可读: $LOG_FILE" >&2
  exit 2
fi

awk -v path_filter="$PATH_FILTER" '
function pct(arr, n, p,    idx) {
  if (n == 0) return 0;
  idx = int(p * n + 0.999999);
  if (idx < 1) idx = 1;
  if (idx > n) idx = n;
  return arr[idx];
}
function bucket_for(path) {
  if (index(path, "/api/volunteer") == 1) return "/api/volunteer";
  if (index(path, "/api/admin") == 1)     return "/api/admin";
  if (index(path, "/api/ml") == 1)        return "/api/ml";
  if (index(path, "/api/site-stats") == 1) return "/api/site-stats";
  if (index(path, "/ml/") == 1)            return "/ml";
  if (index(path, "/api/") == 1)           return "/api/other";
  return "/(static)";
}
{
  # 解析 "$request"：第 6 个空格分隔字段是 method+path+http；取 $7/$8 是 request_time/upstream_response_time。
  # 注意 request_time 在第 11 列（因为 [$time_local] 占 2 列，"$request" 占 3 列）。
  # 这里更稳的做法：从字段尾部回数。$NF 是 $http_x_forwarded_for，依次回退。
  status = $9;
  request_time = $11 + 0;
  upstream_time = $12 + 0;
  # 还原 $request：第 6 个 token 起，到第 8 个 token 止
  request = $6 " " $7 " " $8;
  # 提取 path
  path = $7;
  if (path_filter != "" && index(path, path_filter) == 0) next;

  total++;
  status_count[status]++;
  bucket = bucket_for(path);
  bucket_total[bucket]++;
  bucket_rt_sum[bucket] += request_time;
  if (request_time > bucket_rt_max[bucket]) bucket_rt_max[bucket] = request_time;

  rt[total] = request_time;
  ut[total] = upstream_time;
}
END {
  if (total == 0) {
    print "no matching rows";
    exit;
  }
  n = asort(rt);
  m = asort(ut);
  rt_sum = 0;
  for (i = 1; i <= n; i++) rt_sum += rt[i];
  ut_sum = 0;
  for (i = 1; i <= m; i++) ut_sum += ut[i];
  rt_avg = n > 0 ? rt_sum / n : 0;
  ut_avg = m > 0 ? ut_sum / m : 0;
  printf "rows=%d\n", total;
  printf "request_time   p50=%.4fs p95=%.4fs p99=%.4fs max=%.4fs avg=%.4fs\n",
    pct(rt, n, 0.50), pct(rt, n, 0.95), pct(rt, n, 0.99), rt[n], rt_avg;
  printf "upstream_time  p50=%.4fs p95=%.4fs p99=%.4fs max=%.4fs avg=%.4fs\n",
    pct(ut, m, 0.50), pct(ut, m, 0.95), pct(ut, m, 0.99), ut[m], ut_avg;
  print "status\tcount";
  for (s in status_count) printf "  %s\t%d\n", s, status_count[s];
  print "bucket\trows\tavg_rt\tmax_rt";
  for (b in bucket_total) {
    rows = bucket_total[b];
    avg = rows>0 ? bucket_rt_sum[b]/rows : 0;
    printf "  %-18s\t%d\t%.4fs\t%.4fs\n", b, rows, avg, bucket_rt_max[b];
  }
}
' "$LOG_FILE"
