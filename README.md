Example Blog-Backend for the Web-App course (Simple Class-Showcase Version)

# Build und Start im Dev-Mode
Starting DB- with Quarkus Dev-Services for a MariaDB and a Redpanda as Kafka-Broker (and keycloak from hftm-cloud-labor)  

    ./mvnw quarkus:dev

## Swagger-UI Zugriff
http://localhost:8080/q/swagger-ui

## Access Token from hftm-Lab-Keycloak with httpie

User Alice:

    http -v --form --auth backend-service:<secret> POST https://d-cap-keyclaok.kindbay-711f60b2.westeurope.azurecontainerapps.io/realms/blog/protocol/openid-connect/token username=alice password=alice grant_type=password


## httpie Example-Access
    
    http :8080/entries

httpie-Session for reusing the access-token:  

    http --session=auth-user :8080/entries Authorization:"Bearer ACCESS-TOKEN"
    http --session=auth-user :8080/entries
    http --session=auth-user :8080/entries title="Neuer Titel" content=Inhalt

# Build and Start as Container on local machine for testing/demo with local-profile

## Build Container of this project  

    ./mvnw package -Dquarkus.container-image.build=true -Dquarkus-profile=local

## Setup Docker Network  

    docker network create blog-nw

## Start and configure Redpanda as Kafka-Broker  

    docker run -d --name=redpanda-1 -p 9092:9092 --network blog-nw -d docker.redpanda.com/vectorized/redpanda:v22.1.7 redpanda start --overprovisioned --smp 1 --check=false --memory 1G --reserve-memory 0M --node-id 0 --kafka-addr 0.0.0.0:9092 --advertise-kafka-addr redpanda-1:9092
    docker exec -it redpanda-1 rpk topic create validation-request --brokers=localhost:9092
    docker exec -it redpanda-1 rpk topic create validation-response --brokers=localhost:9092

## Keycloak

    hftm-Azure-Keycloak is used!

## Start MySQL-Container

    docker run --name blog-mysql -p 3306:3306 --network blog-nw -e MYSQL_ROOT_PASSWORD=vs4tw -e MYSQL_USER=dbuser -e MYSQL_PASSWORD=dbuser -e MYSQL_DATABASE=blogdb -d mysql:8.0


## Start Blog-Backend

    docker run -i --rm -p 8080:8080 --network blog-nw -d ghcr.io/hftm-inf/blog-backend-local:latest

# Deployment

Build and push native Image  

    ./mvnw package -Pnative -Dquarkus.native.container-build=true  -Dquarkus.container-image.build=true
    docker push ghcr.io/hftm-inf/blog-backend:latest
