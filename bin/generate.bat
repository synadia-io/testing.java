aws ec2 describe-instances --output json > aws.json

@if %errorlevel% NEQ 0 goto end

java -cp "build/libs/*" io.synadia.tools.Generator

:end