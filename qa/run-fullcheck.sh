#!/bin/bash
# Full functional self-check for the cafe management system.
#
# What it does:
#   1) regenerate demo data             (sql/gen_seed.py)
#   2) rebuild the database to baseline (schema.sql + data.sql)
#   3) start the packaged jar
#   4) run FullCheck: every page, access control, business flows, data consistency
#   5) stop the app and print the report
#
# Usage (from anywhere):  bash qa/run-fullcheck.sh
#
# NOTE: all console output is plain ASCII on purpose. Windows Git Bash mangles
#       UTF-8 Chinese text when a script is piped, which makes failures unreadable.

set -u
PROJ="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="E:/devdlop/jdk/bin/java.exe"
JAVAC="E:/devdlop/jdk/bin/javac.exe"
PY="E:/devdlop/python/python.exe"
MYSQL="mysql"
MYSQL_JAR="C:/Users/ASUS/.m2/repository/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar"
PORT=18080

cd "$PROJ" || exit 1
QA_WIN="$(cygpath -w "$PROJ/qa" 2>/dev/null || echo "$PROJ/qa")"

echo "[1/5] Generating demo data..."
"$PY" sql/gen_seed.py > /dev/null 2>&1 || { echo "  FAILED: gen_seed.py"; exit 1; }

echo "[2/5] Rebuilding database cafe_db..."
$MYSQL --default-character-set=utf8mb4 -uroot -p123456 \
  -e "DROP DATABASE IF EXISTS cafe_db; CREATE DATABASE cafe_db DEFAULT CHARSET utf8mb4;" 2>/dev/null
$MYSQL --default-character-set=utf8mb4 -uroot -p123456 cafe_db < sql/schema.sql 2>/dev/null
$MYSQL --default-character-set=utf8mb4 -uroot -p123456 cafe_db < sql/data.sql 2>/dev/null

echo "[3/5] Starting application on port $PORT..."
rm -f fullcheck-result.txt run.log
"$JAVA" -jar target/cafe-management-system-1.0.0.jar --server.port=$PORT > run.log 2>&1 &
APP_PID=$!
sleep 20
if ! grep -q "Started CafeApplication" run.log; then
  echo "  FAILED to start. Tail of log:"; tail -20 run.log
  kill $APP_PID 2>/dev/null; exit 1
fi
echo "      started"

echo "[4/5] Running full functional check..."
"$JAVAC" -encoding UTF-8 -nowarn -d "$PROJ/qa" "$PROJ/qa/FullCheck.java" 2>/dev/null
"$JAVA" -Dfile.encoding=UTF-8 -cp "$QA_WIN;$MYSQL_JAR" FullCheck 2>&1 | tail -5

echo "[5/5] Stopping application"
kill $APP_PID 2>/dev/null
wait $APP_PID 2>/dev/null
rm -f "$PROJ/qa/FullCheck.class"

echo ""
echo "=========== REPORT ==========="
cat "$PROJ/fullcheck-result.txt" 2>/dev/null || echo "report not generated"
