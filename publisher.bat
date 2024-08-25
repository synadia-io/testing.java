call \Programs\gradle-8.5\bin\gradle clean uberJar
java -cp build/libs/testing.java-1.0.0-uber.jar io.synadia.Runner --workload publisher --id id1 --params src/main/resources/publish-config.json
