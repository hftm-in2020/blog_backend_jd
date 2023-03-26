# Workshop: Github Actions mit CD und Revisions

## Github Actions

Github actions sind sehr hilfreich wenn man gewisse Prozesse automatisiert laufen lassen möchte. Es handelt sich in diesem Fall konkret um yaml Files welche von der Struktur her ähnlich wie JSON Files aufgebaut sind, und dementsprechend einfach zum lesen und warten sind. Die YAML Files müssen immer unter einem konkreten Verzeichniss auffindbar sein: <projekt>/.github/workflows 

Mittels unterschiedlichen Konfigurationen kann man z.B folgende Prozesse automatisieren:

    * Code kompilieren und builden
    * Automatisiert Tests laufen lassen
    * Notifikationen nach bestimmten Ereignissen versenden
    * Abhängigkeiten automatisch updaten
    * Mittels "Scheduled tasks" jobs in Intervallen oder an bestimmten Zeitpunkten ausführen
    * .. So vieles mehr...
    
... Und was ich mir konkret vorgenommen habe für diesen Workshop: Meinen Code in eine existierende Instanz laufen lassen nach jedem push, Continious Deployment.

In meinem ersten Gedanken wollte ich zwar nur meinen Code versuchen zu deployen bei jedem push und es simpel halten, habe mich aber dann dazu entschieden etwas mehr damit herumzuspielen. Folgendes ist dabei herausgekommen:

    name: Build and Deploy Docker image to Azure Container App
    on:
      push:
        branches:
          - DEV
    jobs:
      build-and-push:
        runs-on: ubuntu-latest
        env:
          IMAGE_NAME: jessicasblog.azurecr.io/blog_backend_jd
          IMAGE_TAG: DEV
          RESSOURCE_GROUP: d-rg-blog-jd
          REVISION: d-ca-blog-jd-dev--yq4nk1o
          QUARKUS_PROFILE: dev
          QUARKUS_DATASOURCE_USERNAME: dbuser
          QUARKUS_DATASOURCE_PASSWORD: dbuser
          QUARKUS_DATASOURCE_JDBC_URL: jdbc:mysql://d-mysql-blog-jd.mysql.database.azure.com:3306/blogdb
          QUARKUS_SMALLRYE_OPENAPI_INFO_TITLE: DEV Blog API
          QUARKUS_CONTAINER_IMAGE_NAME: blog-backend-dev
          QUARKUS_CONTAINER_IMAGE_TAG: latest-dev
          AZURE_WEBAPP_NAME: d-ca-blog-jd-dev
          AZURE_RESOURCE_GROUP: d-rg-blog-jd
          AZURE_IMAGE_NAME: jessicasblog.azurecr.io/blog_backend_jd
          AZURE_IMAGE_TAG: DEV
        steps:
          - name: Checkout Code
            uses: actions/checkout@v2
          - name: Use Node.js 16.x
            uses: actions/setup-node@v2
            with:
              node-version: '16.x'
          - name: Azure Login
            uses: azure/login@v1
            with:
              creds: ${{ secrets.DCABLOGJDDEV_AZURE_CREDENTIALS }}
          - name: Login to Azure Container Registry
            uses: azure/docker-login@v1
            with:
              login-server: jessicasblog.azurecr.io
              username: '${{secrets.ACR_USERNAME}}'
              password: '${{secrets.ACR_PASSWORD}}'
          - name: Replace environment variables in application.properties file
            run: >-
              sed -i 's|\%dev.quarkus.smallrye-openapi.info-title=DEV Blog
              API|\%dev.quarkus.smallrye-openapi.info-title='"${QUARKUS_SMALLRYE_OPENAPI_INFO_TITLE}"'|g;
              s|\%dev.quarkus.container-image.name=blog-backend-dev|\%dev.quarkus.container-image.name='"${QUARKUS_CONTAINER_IMAGE_NAME}"'|g;
              s|\%dev.quarkus.container-image.tag=latest-dev|\%dev.quarkus.container-image.tag='"${QUARKUS_CONTAINER_IMAGE_TAG}"'|g'
              src/main/resources/application.properties
          - name: Build Docker image
            run: >-
              docker build --file src/main/docker/Dockerfile.jvm
              --build-arg QUARKUS_PROFILE=${{env.QUARKUS_PROFILE}}
              --build-arg QUARKUS_DATASOURCE_USERNAME=${{env.QUARKUS_DATASOURCE_USERNAME}}
              --build-arg QUARKUS_DATASOURCE_PASSWORD=${{env.QUARKUS_DATASOURCE_PASSWORD}}
              --build-arg QUARKUS_DATASOURCE_JDBC_URL=${{env.QUARKUS_DATASOURCE_JDBC_URL}}
              -t ${{env.AZURE_IMAGE_NAME}}:${{env.AZURE_IMAGE_TAG}} .
          - name: Push Docker image to Azure Container Registry
            run: 
              docker push ${{env.AZURE_IMAGE_NAME}}:${{env.AZURE_IMAGE_TAG}}
          - name: Deploy to Azure Container App
            uses: azure/CLI@v1
            with:
              inlineScript: |
                az config set extension.use_dynamic_install=yes_without_prompt
                az containerapp update -n d-ca-blog-jd-dev -g d-rg-blog-jd --image jessicasblog.azurecr.io/blog_backend_jd:DEV
                az containerapp revision restart -n d-ca-blog-jd-dev -g d-rg-blog-jd --revision d-ca-blog-jd-dev--yq4nk1o


    
Bevor ich die unterschiedlichen Ausschnitte der Action genauer erkläre, erzähle ich erst noch etwas zum Aufbau der Infrastruktur und was ich alles gemacht habe bevor ich meine GitHub Action richtig testen konnte. 

Den Teil zum erstellen einer Subscription und Ressourcen Gruppe werde ich bewusst überspringen und gehe davon aus, dass du mit dem Grundaufbau von Azure bekannt bist.

## Container Registry

Als Registry habe ich mich für Azure Container Registry entschieden und wie folgt in Powershell mit Hilfe von Azure CLI erstellt:

    // Login to azure account
    az login    
    
    // Set the subscription you want to use
    az account set --subscription "Azure for Students"

    // Create the Azure Container Registry
    // --admin-enabled true   -> With this command you will be enabled to access your registry via Azure CLI 
    az acr create --resource-group "d-rg-blog-jd" --name "jessicasblog" --sku Basic --admin-enabled true

Am Ende sieht das ganze in Azure etwa so aus:

    ![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img5.png)


## Infrastruktur in Azure

Um meinen Code irgendwo deployen zu können, habe ich von Container Apps in Azure gebrauch gemacht. Es gibt zwei Möglichkeiten wie du eine Container App in Betrieb nehmen kannst:
    * Via Azure Portal UI
    * Via Azure CLI ( bash oder Powershell)
    
Ich werde dir beide Arten zeigen. Fangen wir mal mit der visuelleren Variante an, über das Azure Portal UI. 

Ich habe:

    * Eine Subscription: Azure for Students
    * Eine Ressourcen Gruppe (RG) : d-rg-blog-jd
    * Eine Container Apps Enviroment: d-ce-blog-jd

Innerhalb meiner RG klicke ich auf "Erstellen"
    ![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img1.png)
    
Nach Klick auf Erstellen wird man zum Marketplace weitergeleitet. Hier kannst du im Suchfeld Container App eintippen und auswählen. Hier nochmal auf Erstellen klicken

    ![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img2.png)

Nun kannst du deine Container App so konfigurieren wie du sie brauchst. In meinem Falle so:

    ![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img3.png)
    
    ![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img4.png)






# Inbetriebnahme des Backends
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
