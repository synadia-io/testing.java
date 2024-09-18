cd ~
rm -rf testing.java
git clone https://github.com/synadia-io/testing.java
cd testing.java

cat > generator.json <<EOF
{
  "server_filter": "scottf-server-",
  "client_filter": "scottf-client-"
}
EOF

chmod +x bin/* && chmod -x bin/*.bat && bin/make && bin/get-aws && bin/gen && chmod +x gen/*