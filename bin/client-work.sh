echo "Todo: Export AWS security"
echo "Todo Once: export PATH=\$PATH:~/testing.java/bin:~/testing.java/gen"

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

chmod +x bin/* && chmod -x bin/*.bat && bin/make && bin/generate && chmod +x gen/*
