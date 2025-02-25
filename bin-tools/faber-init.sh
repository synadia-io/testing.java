# 0. SHELL STUFF
echo "alias l='ls -la'" >> /home/ec2-user/.bash_profile
echo "alias dir='ls -la'" >> /home/ec2-user/.bash_profile
echo "alias cls='clear'" >> /home/ec2-user/.bash_profile
alias l='ls -la'
alias dir='ls -la'
alias cls='clear'

# 1. INSTALL SOFTWARE
# java
sudo yum -y install java-21-amazon-corretto-devel
java -version

# git
sudo yum -y install git

# gradle
wget https://services.gradle.org/distributions/gradle-8.10-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-*.zip
echo 'export GRADLE_HOME=/opt/gradle/gradle-8.10' >> .bash_profile
echo 'export PATH=${GRADLE_HOME}/bin:${PATH}' >> .bash_profile
export GRADLE_HOME=/opt/gradle/gradle-8.10
export PATH=${GRADLE_HOME}/bin:${PATH}
gradle -version

#cat > ~/jstatd.all.policy <<EOF
#grant codebase "file:${java.home}/../lib/tools.jar" {
#   permission java.security.AllPermission;
#};
#EOF

mkdir bin

cat > ~/bin/f <<EOF
ps -aux | grep ConsumerInfoSim
EOF

chmod +x ~/bin/f

cat > ~/bin/r <<EOF
cd ~
rm -rf testing.java
git clone https://github.com/synadia-io/testing.java
cd testing.java

cat > generator.json <<REOF
{
  "instance_prefix": "scottf-9",
  "faber_filter": "scottf-9-faber-",
}
REOF

chmod +x bin/* && chmod -x bin/*.bat && bin/make && bin/get-aws && bin/gen && chmod +x gen/*
cd ~/testing.java
EOF

chmod +x ~/bin/r
