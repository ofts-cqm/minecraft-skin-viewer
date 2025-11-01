#!/bin/bash

echo 创建db.properties
cat > db.properties << EOF
jdbcUrl=$DB_URL
username=$DB_USER
password=$DB_PASSWORD
EOF

echo 创建config.yml
cat > config.yml << EOF
address: "$SERVER_ADDRESS"
port: $SERVER_PORT
EOF

if [ -n "$PROXY_TYPE" ]; then
  cat >> config.yml << EOF
proxy:
  type: $PROXY_TYPE
  address: $PROXY_ADDRESS
  port: $PROXY_PORT
EOF
fi

# 修正文件归属
chown -R java /app

if [ ! -n "$START_CMD" ]; then
  START_CMD="java -jar app.jar"
fi
gosu java xvfb-run $START_CMD
