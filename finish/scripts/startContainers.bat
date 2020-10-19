@ECHO OFF
set KAFKA_SERVER=kafka:9092
set NETWORK=reactive-app

docker network create %NETWORK%

docker run -d ^
  -e ALLOW_ANONYMOUS_LOGIN=yes ^
  --network=%NETWORK% ^
  --name=zookeeper ^
  --rm ^
  bitnami/zookeeper:3 

start /b docker run -d ^
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181 ^
  -e ALLOW_PLAINTEXT_LISTENER=yes ^
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 ^
  --hostname=kafka ^
  --network=%NETWORK% ^
  --name=kafka ^
  --rm ^
  bitnami/kafka:2 
