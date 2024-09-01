# 0. SHELL STUFF
echo "alias l='ls -la'" >> /home/ubuntu/.bash_profile
echo "alias dir='ls -la'" >> /home/ubuntu/.bash_profile
echo "alias cls='clear'" >> /home/ubuntu/.bash_profile
alias l='ls -la'
alias dir='ls -la'
alias cls='clear'

# 1. INSTALL NATS
# java
sudo yum -y install java-21-amazon-corretto-devel

# gradle
wget https://services.gradle.org/distributions/gradle-8.10-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-*.zip
echo 'export GRADLE_HOME=/opt/gradle/gradle-8.10' >> .bash_profile
echo 'export PATH=${GRADLE_HOME}/bin:${PATH}' >> .bash_profile
export GRADLE_HOME=/opt/gradle/gradle-8.10
export PATH=${GRADLE_HOME}/bin:${PATH}

set +x
cls
gradle -version
echo ""
java -version

echo ""
cd ~
rm -rf testing.java
git clone https://github.com/synadia-io/testing.java
cd testing.java

echo ""
echo "TODO: Export AWS security"
echo "TODO: chmod +x bin/generate && bin/generate && rm gen/*.bat && chmod +x gen/* && chmod -x gen/*.json && chmod -x gen/*.txt"
echo ""