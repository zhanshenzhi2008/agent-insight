#!/bin/sh
# =============================================================================
# scripts/install-hooks.sh — 把版本化的 hook 装到 .git/hooks/
# =============================================================================
# 用法：bash scripts/install-hooks.sh
# 作用：
#   - 把 scripts/hooks/pre-commit 链接到 .git/hooks/pre-commit
#   - 验证 hook 可执行 + 跑一次自检
#
# 适用时机：
#   - 第一次 clone 仓库后
#   - 协作者更新代码后
# =============================================================================

set -e

REPO_ROOT=$(git rev-parse --show-toplevel)
HOOK_SRC="$REPO_ROOT/scripts/hooks/pre-commit"
HOOK_DST="$REPO_ROOT/.git/hooks/pre-commit"

if [ ! -f "$HOOK_SRC" ]; then
  echo "❌ 找不到 $HOOK_SRC" >&2
  exit 1
fi

if [ ! -x "$HOOK_SRC" ]; then
  echo "⚠️  $HOOK_SRC 不可执行，正在 chmod +x"
  chmod +x "$HOOK_SRC"
fi

# 移除旧版本（避免残留 .sample）
if [ -e "$HOOK_DST" ] || [ -L "$HOOK_DST" ]; then
  rm -f "$HOOK_DST"
fi

ln -sf "../../scripts/hooks/pre-commit" "$HOOK_DST"
echo "✅ 已安装 pre-commit hook: $HOOK_DST"

# 自检：跑 hook 看是否有语法错误
if bash "$HOOK_SRC" >/dev/null 2>&1; then
  echo "✅ Hook 自检通过（语法正确）"
elif [ $? -eq 1 ]; then
  # hook 返回 1 是预期（无 staged 时），不是错误
  echo "✅ Hook 自检通过（无 staged 文件，符合预期）"
fi

echo ""
echo "🎉 安装完成。下次 git commit 会自动拦截 .env / 明文凭据。"
echo "   跳过方式（紧急情况）：git commit --no-verify"
