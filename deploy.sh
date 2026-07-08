#!/bin/bash

# Configuration
SERVER_USER="root"
SERVER_IP="43.157.251.205"
SERVER_DIR="/opt/umkmshop"
SSH_KEY="$HOME/.ssh/key.pem"
JAR_NAME="umkmshop-backend-all.jar"
LOCAL_JAR="backend/build/libs/$JAR_NAME"

# Build local Shadow JAR
echo "Building Shadow JAR on local machine..."
./gradlew :backend:shadowJar

if [ $? -ne 0 ]; then
    echo "Build failed! Deployment aborted."
    exit 1
fi

# Check if JAR exists
if [ ! -f "$LOCAL_JAR" ]; then
    echo "Error: JAR file not found at $LOCAL_JAR"
    exit 1
fi

echo "Uploading JAR to server..."
# Use scp with identity file
scp -i "$SSH_KEY" "$LOCAL_JAR" "$SERVER_USER@$SERVER_IP:$SERVER_DIR/app.jar"

if [ $? -ne 0 ]; then
    echo "Upload failed! Check your server IP, SSH key path, and access."
    exit 1
fi

echo "Restarting service on server..."
ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_IP" "sudo systemctl restart umkmshop"

if [ $? -ne 0 ]; then
    echo "Service restart failed! Check if systemd service 'umkmshop' is correctly configured on the server."
    exit 1
fi

echo "Deployment successful!"
