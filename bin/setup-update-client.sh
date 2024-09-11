cd ~
mkdir -p bin
rm bin/update-client
cat > bin/update-client <<EOF

cd ~
rm -rf testing.java
git clone https://github.com/synadia-io/testing.java
cd testing.java

chmod +x bin/* && chmod -x bin/*.bat && bin/make && bin/get-aws && bin/generate && chmod +x gen/*
EOF

chmod +x bin/*

echo "Todo: Export AWS security"
. update-client