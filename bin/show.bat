aws ec2 describe-instances --output json > aws.json
call gradle buildDeploy
java -cp "build/libs/*" io.synadia.tools.Generator show
