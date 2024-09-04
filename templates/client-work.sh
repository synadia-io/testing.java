echo "TODO: Export AWS security"

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

chmod +x bin/generate && bin/generate && chmod +x gen/* && chmod -x gen/*.json
