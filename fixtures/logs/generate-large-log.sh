#!/bin/bash
# ============================================================================
# 生成 req_test_large.log — 模拟大日志文件（10MB），用于分页 / 流式读取测试
# 用法：bash generate-large-log.sh
# 产物：当前目录下 req_test_large.log
# ============================================================================
set -euo pipefail

OUT="req_test_large.log"
TARGET_MB=10
TARGET_BYTES=$((TARGET_MB * 1024 * 1024))

if [ -f "$OUT" ]; then
  echo "[skip] $OUT already exists, size=$(du -h "$OUT" | cut -f1)"
  exit 0
fi

echo "[gen]  生成 $OUT (${TARGET_MB}MB)，请稍候..."

# 用单行循环，避免外部依赖（不需要 seq / yes）
{
  for i in $(seq 1 200000); do
    echo "2026-06-30 12:00:$((i % 60)).$(printf '%03d' $((i % 1000))) INFO  [req_test_large] TaskRunner - step $i of 200000 processed (duration=$((RANDOM % 200))ms)"
  done
} > "$OUT"

# 如果还不够 10MB，追加 padding 行
current=$(stat -f%z "$OUT" 2>/dev/null || stat -c%s "$OUT")
if [ "$current" -lt "$TARGET_BYTES" ]; then
  echo "[pad]  current=$current bytes, padding to ${TARGET_BYTES}..."
  python3 -c "
import sys
with open('$OUT','a') as f:
    f.write('2026-06-30 12:30:00.000 INFO  [req_test_large] padding-line\n' * 50000)
" 2>/dev/null || true
fi

echo "[done] $(du -h "$OUT" | cut -f1)  $OUT"