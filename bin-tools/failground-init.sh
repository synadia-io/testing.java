# 0. SHELL STUFF
echo "alias l='ls -la'" >> /home/ubuntu/.bash_profile
echo "alias dir='ls -la'" >> /home/ubuntu/.bash_profile
echo "alias cls='clear'" >> /home/ubuntu/.bash_profile
alias l='ls -la'
alias dir='ls -la'
alias cls='clear'
mkdir bin
echo 'export PATH=/home/ubuntu/bin:${PATH}' >> .bash_profile
export PATH=/home/ubuntu/bin:${PATH}

# PREPARE
sudo apt update -y && sudo apt upgrade -y
sudo apt-get update
sudo apt install unzip

# AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# JAVA
sudo apt install openjdk-21-jdk -y
java -version

# GIT
sudo apt-get install git -y
git --version

# GRADLE
wget https://services.gradle.org/distributions/gradle-8.10-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-*.zip
echo 'export GRADLE_HOME=/opt/gradle/gradle-8.10' >> .bash_profile
echo 'export PATH=${GRADLE_HOME}/bin:${PATH}' >> .bash_profile
export GRADLE_HOME=/opt/gradle/gradle-8.10
export PATH=${GRADLE_HOME}/bin:${PATH}
gradle -version

# NATS
curl -sf https://binaries.nats.dev/nats-io/nats-server/v2@main | PREFIX=. sh
sudo mv nats-server /usr/bin/
ls -la /usr/bin/nats-server
which nats-server
nats-server -v

# NATS CLI
curl -sf https://binaries.nats.dev/nats-io/natscli/nats@latest | sh

# DOCKER
# Add Docker's official GPG key:
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Add the repository to Apt sources:
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# And Docker itself https://docs.docker.com/engine/install/ubuntu/#installation-methods
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# PROCESS COMPOSE
sh -c "$(curl --location https://raw.githubusercontent.com/F1bonacc1/process-compose/main/scripts/get-pc.sh)" -- -d

# TOXIPROXY
wget -O toxiproxy-2.11.0.deb https://github.com/Shopify/toxiproxy/releases/download/v2.11.0/toxiproxy_2.11.0_linux_amd64.deb
sudo dpkg -i toxiproxy-2.11.0.deb

# GO
sudo snap install --classic go

# TESTING.JAVA stuff
cat > ~/bin/r <<EOF
cd ~
rm -rf testing.java
git clone https://github.com/synadia-io/testing.java
cd testing.java

cat > generator.json <<REOF
{
  "instance_prefix": "scottf-9",
  "failground_filter": "scottf-9-failground",
}
REOF

chmod +x bin/* && chmod -x bin/*.bat && bin/make && bin/get-aws && bin/gen
cd ~/testing.java
EOF

chmod +x ~/bin/r

# END