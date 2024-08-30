# 0. SHELL STUFF
echo "alias l='ls -la'" >> /home/ubuntu/.bash_profile
echo "alias dir='ls -la'" >> /home/ubuntu/.bash_profile
echo "alias cls='clear'" >> /home/ubuntu/.bash_profile
alias l='ls -la'
alias dir='ls -la'
alias cls='clear'

# 1. INSTALL NATS
curl -sf https://binaries.nats.dev/nats-io/nats-server/v2@main | PREFIX=. sh
sudo mv nats-server /usr/bin/
nats-server -v
which nats-server


# 2 create nats-server.service
cat > nats-server.service <<EOF
[Unit]
Description=NATS Server
After=network-online.target ntp.service

[Service]
PrivateTmp=true
Type=simple
ExecStart=/usr/bin/nats-server -js -c /etc/nats.conf
ExecReload=/usr/bin/kill -s HUP $MAINPID
ExecStop=/usr/bin/kill -s SIGINT $MAINPID
User=nats
Group=nats
# The nats-server uses SIGUSR2 to trigger using Lame Duck Mode (LDM) shutdown
KillSignal=SIGUSR2
# You might want to adjust TimeoutStopSec too.

[Install]
WantedBy=multi-user.target
EOF

sudo mv nats-server.service /etc/systemd/system/

# 3 create conf file
cat > nats.conf <<EOF
port:4222
http:8222
server_name=scottf-<InstanceId>
cluster {
  name: scottf-cluster
  listen: 0.0.0.0:7222
  routes: [
    nats-route://<PrivateIpRoute1>:7222
    nats-route://<PrivateIpRoute2>:7222
  ]
}
EOF

sudo mv nats.conf /etc/

# 4 Finish
sudo useradd -r -s /bin/false nats
sudo mkdir /tmp/nats
sudo chown -R nats:nats /tmp/nats
sudo systemctl daemon-reload
sudo systemctl enable nats-server
sudo systemctl start nats-server.service
sudo systemctl status nats-server
