cd ~
mkdir -p bin
rm bin/refresh
cat > bin/refresh <<EOF

cd ~
rm -rf testing.java
git clone https://github.com/synadia-io/testing.java
cd testing.java

cat > generator.json <<REOF
{
  "server_filter": "scottf-server-",
  "client_filter": "scottf-client-"
}
REOF

chmod +x bin/* && chmod -x bin/*.bat && bin/make && bin/get-aws && bin/gen && chmod +x gen/*
EOF

chmod +x bin/*

echo "Todo: Export AWS security"
. refresh