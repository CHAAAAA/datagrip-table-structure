#!/usr/bin/env bash
# 无测试框架下的自检：验证「总览默认选中哪个 schema」这条分支逻辑（ObjectBrowserFileEditor.pickSchema）。
# 用 DataGrip 自带 JBR 编译主源码 + 自检类后直接运行，不依赖 Gradle / 运行时 IDE。
set -euo pipefail

DG="${DATAGRIP_HOME:-$HOME/Applications/DataGrip.app}"
JAVAC="$DG/Contents/jbr/Contents/Home/bin/javac"
JAVA="$DG/Contents/jbr/Contents/Home/bin/java"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/datagrip-pick-schema-check

if [[ ! -x "$JAVAC" ]]; then
  echo "找不到 DataGrip 的 javac：$JAVAC" >&2
  exit 1
fi

CP=""
for d in "$DG/Contents/lib" "$DG/Contents/plugins/DatabaseTools/lib" "$DG/Contents/plugins/DatabaseTools/lib/modules"; do
  while IFS= read -r -d '' jar; do CP+="$jar:"; done < <(find "$d" -maxdepth 1 -name '*.jar' -print0)
done
CP="${CP%:}"

rm -rf "$OUT" && mkdir -p "$OUT"
"$JAVAC" -proc:none -d "$OUT" -cp "$CP" \
  $(find "$ROOT/src/main/java" -name '*.java') \
  $(find "$ROOT/scripts/selfcheck" -name '*.java')
"$JAVA" -cp "$OUT:$CP" com.example.tablestructure.editor.PickSchemaCheck
