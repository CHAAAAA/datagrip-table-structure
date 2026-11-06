#!/usr/bin/env bash
# 不依赖 Gradle 的编译自检：用 DataGrip 自带 JBR 25 的 javac + 真实平台 jar 编译插件源码。
# 用途：在没有 Gradle / 网络受限时快速验证代码与 DataGrip 2026.2 API 的兼容性。
set -euo pipefail

DG="${DATAGRIP_HOME:-$HOME/Applications/DataGrip.app}"
JAVAC="$DG/Contents/jbr/Contents/Home/bin/javac"
OUT="${1:-/tmp/datagrip-plugin-compile-check}"

if [[ ! -x "$JAVAC" ]]; then
  echo "找不到 DataGrip 的 javac：$JAVAC" >&2
  echo "请设置 DATAGRIP_HOME 指向 DataGrip.app（默认 ~/Applications/DataGrip.app）" >&2
  exit 1
fi

CP=""
for d in "$DG/Contents/lib" "$DG/Contents/plugins/DatabaseTools/lib" "$DG/Contents/plugins/DatabaseTools/lib/modules"; do
  while IFS= read -r -d '' jar; do CP+="$jar:"; done < <(find "$d" -maxdepth 1 -name '*.jar' -print0)
done
CP="${CP%:}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$OUT" && mkdir -p "$OUT"

echo "javac: $("$JAVAC" -version 2>&1)"
echo "输出目录: $OUT"
"$JAVAC" -Xlint:deprecation -proc:none -d "$OUT" -cp "$CP" \
  $(find "$ROOT/src/main/java" -name '*.java')
echo "OK: 编译通过（exit 0）"
