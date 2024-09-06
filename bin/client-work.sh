echo "Todo: Export AWS security"
echo "Todo Once: export PATH=\$PATH:~/testing/bin:~/testing/gen"

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

chmod +x bin/generate && generate && chmod +x gen/* && chmod -x gen/*.json
