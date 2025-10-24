# STOP THE SERVER
sudo systemctl stop nats-server.service

# EDIT SERVICE/CONF IF NEEDED
sudo vi /etc/systemd/system/nats-server.service
sudo vi /etc/nats.conf

# INSTALL THE SERVER MANUAL CHANGE VERSION
curl -L https://github.com/nats-io/nats-server/releases/download/v2.12.1/nats-server-v2.12.1-linux-amd64.tar.gz -o nats-server.tar.gz
tar -xvzf nats-server.tar.gz
sudo cp nats-server-v2.12.1-linux-amd64/nats-server /usr/bin

# START THE SERVER
sudo systemctl daemon-reload
sudo systemctl start nats-server.service
sudo systemctl status nats-server.service
