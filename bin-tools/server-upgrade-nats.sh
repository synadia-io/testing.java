# STOP THE SERVER
sudo systemctl stop nats-server.service

# INSTALL THE SERVER
curl -sf https://binaries.nats.dev/nats-io/nats-server/v2@main | PREFIX=. sh
sudo mv nats-server /usr/bin/
ls -la /usr/bin/nats-server
which nats-server
nats-server -v

# START THE SERVER
sudo systemctl start nats-server.service
