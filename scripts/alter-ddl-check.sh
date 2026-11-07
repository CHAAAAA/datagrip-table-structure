#!/usr/bin/env bash
# 无测试框架下的自检：验证 ALTER DDL 生成逻辑（AlterDdlGenerator）的拼接结果。
# 与 pick-schema-check.sh 同一套路：DataGrip JBR 编译主源码 + 自检类后直接运行。
set -euo pipefail

DG="${DATAGRIP_HOME:-$HOME/Applications/DataGrip.app}"
JAVAC="$DG/Contents/jbr/Contents/Home/bin/javac"
JAVA="$DG/Contents/jbr/Contents/Home/bin/java"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/datagrip-alter-ddl-check

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

rm -rf "$OUT" && mkdir -p "$OUT"
"$JAVAC" -proc:none -d "$OUT" -cp "$CP" \
  $(find "$ROOT/src/main/java" -name '*.java') \
  "$ROOT/src/selfCheck/java/com/github/chaaaaa/datagripts/service/AlterDdlCheck.java"
"$JAVA" -cp "$OUT:$CP" com.github.chaaaaa.datagripts.service.AlterDdlCheck
