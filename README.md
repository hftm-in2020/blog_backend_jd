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

## Aufbau in Github
   
Auf Github habe ich 3 Branches erstellt mit dem Backend Code:
   
   * DEV
   * TEST
   * PROD
   
## Container Registry

Als Registry habe ich mich für Azure Container Registry entschieden und wie folgt in Powershell mit Hilfe von Azure CLI erstellt:

    // Login to azure account
    az login    
    
    // Set the subscription you want to use
    az account set --subscription "Azure for Students"

    // Create the Azure Container Registry
    // --admin-enabled true   -> With this command you will be enabled to access your registry via Azure CLI 
    az acr create --resource-group "d-rg-blog-jd" --name "jessicasblog" --sku Basic --admin-enabled true

Am Ende sieht die Resourcen JSON in Azure etwa so aus:
   
   {
       "sku": {
           "name": "Basic",
           "tier": "Basic"
       },
       "type": "Microsoft.ContainerRegistry/registries",
       "id": "/subscriptions/5a7e2fca-b2bf-4e61-8197-dbc31ef1d072/resourceGroups/d-rg-blog-jd/providers/Microsoft.ContainerRegistry/registries/jessicasblog",
       "name": "jessicasblog",
       "location": "westeurope",
       "tags": {},
       "properties": {
           "loginServer": "jessicasblog.azurecr.io",
           "creationDate": "2023-03-18T20:40:39.9967142Z",
           "provisioningState": "Succeeded",
           "adminUserEnabled": false,
           "policies": {
               "quarantinePolicy": {
                   "status": "disabled"
               },
               "trustPolicy": {
                   "type": "Notary",
                   "status": "disabled"
               },
               "retentionPolicy": {
                   "days": 7,
                   "lastUpdatedTime": "2023-03-23T20:40:48.085274+00:00",
                   "status": "disabled"
               }
           }
       }
   }
 
   
## Infrastruktur in Azure

Um meinen Code irgendwo deployen zu können, habe ich von Container Apps in Azure gebrauch gemacht. Es gibt zwei Möglichkeiten wie du eine Container App in Betrieb nehmen kannst:
    * Via Azure Portal UI
    * Via Azure CLI ( bash oder Powershell)
    
Ich werde dir beide Arten zeigen. Fangen wir mal mit der visuelleren Variante an, über das Azure Portal UI. 

Ich habe:

    * Eine Subscription: Azure for Students
    * Eine Ressourcen Gruppe (RG) : d-rg-blog-jd
    * Eine Container Apps Enviroment: d-ce-blog-jd

Innerhalb der Ressourcen Gruppe klicke auf "Erstellen" um für meine DEV Umgebung eine Container App zu erstellen:
    ![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img1.png)
    
Nach Klick auf Erstellen wird man zum Marketplace weitergeleitet. Hier kannst du im Suchfeld Container App eintippen und auswählen. Hier nochmal auf Erstellen klicken
   
![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img2.png)

   
Nun kannst du deine Container App so konfigurieren wie du sie brauchst. In meinem Falle so:

![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img3.png)

    
![alt text](https://github.com/jessicadominguezstevanovic/images/blob/main/images/img4.png)

![image](https://user-images.githubusercontent.com/104629842/227789222-f62099f7-9b51-41cf-8579-76b02c6b209a.png)


 Die Ressourcen JSON findest du hier:
   
      {
       "id": "/subscriptions/5a7e2fca-b2bf-4e61-8197-dbc31ef1d072/resourceGroups/d-rg-blog-jd/providers/Microsoft.App/containerapps/d-ca-blog-jd-dev",
       "name": "d-ca-blog-jd-dev",
       "type": "Microsoft.App/containerApps",
       "location": "West Europe",
       "systemData": {
           "createdBy": "Jessica.Dominguez@hftm.ch",
           "createdByType": "User",
           "createdAt": "2023-03-19T13:30:10.1455186",
           "lastModifiedBy": "fb441ae4-59c3-420a-8898-82a92ef09040",
           "lastModifiedByType": "Application",
           "lastModifiedAt": "2023-03-23T13:58:50.3181282"
       },
       "properties": {
           "provisioningState": "Succeeded",
           "managedEnvironmentId": "/subscriptions/5a7e2fca-b2bf-4e61-8197-dbc31ef1d072/resourceGroups/d-rg-blog-jd/providers/Microsoft.App/managedEnvironments/d-ce-blog-jd",
           "environmentId": "/subscriptions/5a7e2fca-b2bf-4e61-8197-dbc31ef1d072/resourceGroups/d-rg-blog-jd/providers/Microsoft.App/managedEnvironments/d-ce-blog-jd",
           "workloadProfileType": null,
           "outboundIpAddresses": [
               "20.123.246.126"
           ],
           "latestRevisionName": "d-ca-blog-jd-dev--dev",
           "latestRevisionFqdn": "d-ca-blog-jd-dev--dev.redbeach-db0843bd.westeurope.azurecontainerapps.io",
           "customDomainVerificationId": "7BF426BBD9BDD0FD1F939B71925AEF6EF3F14DFEDA891520BCBA5BE089F73BFC",
           "configuration": {
               "secrets": [
                   {
                       "name": "jessicasblogazurecrio-jessicasblog"
                   },
                   {
                       "name": "reg-pswd-de95059e-9d49"
                   }
               ],
               "activeRevisionsMode": "Single",
               "ingress": {
                   "fqdn": "d-ca-blog-jd-dev.redbeach-db0843bd.westeurope.azurecontainerapps.io",
                   "external": true,
                   "targetPort": 8080,
                   "exposedPort": 0,
                   "transport": "Auto",
                   "traffic": [
                       {
                           "weight": 100,
                           "latestRevision": true
                       }
                   ],
                   "customDomains": null,
                   "allowInsecure": false,
                   "ipSecurityRestrictions": null
               },
               "registries": [
                   {
                       "server": "jessicasblog.azurecr.io",
                       "username": "jessicasblog",
                       "passwordSecretRef": "jessicasblogazurecrio-jessicasblog",
                       "identity": ""
                   }
               ],
               "dapr": null,
               "maxInactiveRevisions": null
           },
           "template": {
               "revisionSuffix": "",
               "containers": [
                   {
                       "image": "jessicasblog.azurecr.io/blog_backend_jd:DEV",
                       "name": "d-ca-blog-jd-dev",
                       "resources": {
                           "cpu": 0.5,
                           "memory": "1Gi",
                           "ephemeralStorage": "2Gi"
                       },
                       "probes": []
                   }
               ],
               "initContainers": null,
               "scale": {
                   "minReplicas": 0,
                   "maxReplicas": 10,
                   "rules": null
               },
               "volumes": null
           },
           "eventStreamEndpoint": "https://westeurope.azurecontainerapps.dev/subscriptions/5a7e2fca-b2bf-4e61-8197-dbc31ef1d072/resourceGroups/d-rg-blog-jd/containerApps/d-ca-blog-jd-dev/eventstream"
       },
       "identity": {
           "type": "None"
       }
   } 
   
   

Das gleiche was ich für DEV gemacht habe, mache ich jetzt auch noch für eine TEST und eine PROD Umgebung. Mann kann sich überlegen ob man bei produktiven Umgebungen eine leistungsstärkere Maschine haben möchte mit mehr CPU und RAM zum Beispiel. Da ich in meinem Fall nur eine sehr kleine Applikation laufen lasse, habe ich überall die gleiche Grösse für die Ressourcen gewählt.


# Erstes Image in Container Registry laden, dann in Container App verwenden
   
Um zu sehen ob und wie ich mein Image in die Azure Container Registry bekomme, habe ich von meinem DEV Branch erstmal lokal ein Image gebuildet und dann in die Registry wie folgt hochgeladen:

   //Docker Build image
   docker build -t jessicasblog.azurecr.io/blog_backend_jd:DEV -f Dockerfile.jvm .

   //Docker Image in Azure Container Registry pushen
   docker push jessicasblog.azurecr.io/blog_backend_jd:DEV


Der letztere Command setzt sich aus folgendem zusammen:
   
   * Mein Container Registry befindet sich hier: jessicasblog.azurecr.io
   * Image welches ich pushe heisst: blog_backend_jd
   * Verwendetes Tag: DEV

Das hat soweit geklappt und in meiner Registry sieht es jetzt so aus:
   
   ![image](https://user-images.githubusercontent.com/104629842/227782924-95b36279-dcca-4b20-a70a-0c0847bfe298.png)

   
## Build, Upload and Deploy via Github Action
   
Nun möchte ich, dass automatisch bei jedem Commit ein Image generiert wird, in meine Registr hochgeladen wird, und dieses Image dann in meine Container App deployed wird. Um das zu automatisieren habe ich die Github Action erstellt die ich dir zu Beginn des Readme's gezeigt habe. Hier nun eine Erklärung dazu:
   
In diesem Abschnitt gebe ich meiner Action einen Namen und definiere wann sie ausgeführt werden soll, nähmlich on PUSH und zwar auf den Branch DEV.
Es gibt die Möglichkeit auch mehrere Branches anzugeben wenn der Bedarf da ist.
   
   name: Build and Deploy Docker image to Azure Container App
   on:
     push:
       branches:
         - DEV

Der Name des Jobs ist build-and-push. Mit der runs-on Option können wir der Action sagen auf was für einem Enviroment er den Job ausführen soll, in unserem Fall läuft er auf einer Linux Maschine mit der neusten (latest) Ubuntu Version. 
   
   jobs:
     build-and-push:
       runs-on: ubuntu-latest
   
Ich habe noch nie zuvor mit Github Actions gearbeitet, und somit wollte ich ein bisschen ausprobieren. Einerseits wollte ich sicherstellen, dass ich Enviroment Variablen im Application.properties File des Projektes via Action setzten kann. Dafür habe ich das vorhandene application.properties File wie folgt angepasst:
   
   quarkus.profile=${QUARKUS_PROFILE}
   quarkus.datasource.username=${QUARKUS_DATASOURCE_USERNAME:jd}
   quarkus.datasource.password=${QUARKUS_DATASOURCE_PASSWORD:jd}
   quarkus.datasource.jdbc.url=${QUARKUS_DATASOURCE_JDBC_URL:jdbc:mysql://dev-mysql:3306/blogdb}
   quarkus.smallrye-openapi.info-title=${QUARKUS_SMALLRYE_OPENAPI_INFO_TITLE}
   quarkus.container-image.name=${QUARKUS_CONTAINER_IMAGE_NAME}
   quarkus.container-image.tag=${QUARKUS_CONTAINER_IMAGE_TAG}

Man erkennt die Variablen anhand des ${VARIABLEN_NAME}

Wenn dir die Variablen Namen bekannt vorkommen, dann liegt dies daran, dass ich sie innerhalb meiner Github Action wie folgt eingesetzt habe (Beispiel DEV): 

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
   
Unter dem nächsten Abschnitt meiner Action definiere ich die nötigen Steps die er durchführen sollte:

Selbsterklärend, wird für das Checkouts des Codes verwendet
   
    - name: Checkout Code
        uses: actions/checkout@v2

In Azure einloggen mit den gegebenen Credentials. Das DCABLOGJDDEV_AZURE_CREDENTIALS secret muss in Github unter Repository Secrets definiert sein
   
    - name: Azure Login
        uses: azure/login@v1
        with:
          creds: ${{ secrets.DCABLOGJDDEV_AZURE_CREDENTIALS }}
   
Login zur Azure Registry, auch hier wieder credentials die aus Azure entnommen und in Github gepflegt werden müssen.
   
   - name: Login to Azure Container Registry
        uses: azure/docker-login@v1
        with:
          login-server: jessicasblog.azurecr.io
          username: '${{secrets.ACR_USERNAME}}'
          password: '${{secrets.ACR_PASSWORD}}
   
Im folgenden Teil ersetzte ich die Variablen im application.properties File durch die Enviroment Variablen die ich in der Action gesetzt habe:
   
   - name: Replace environment variables in application.properties file
        run: >-
          sed -i 's|\%dev.quarkus.smallrye-openapi.info-title=DEV Blog
          API|\%dev.quarkus.smallrye-openapi.info-title='"${QUARKUS_SMALLRYE_OPENAPI_INFO_TITLE}"'|g;
          s|\%dev.quarkus.container-image.name=blog-backend-dev|\%dev.quarkus.container-image.name='"${QUARKUS_CONTAINER_IMAGE_NAME}"'|g;
          s|\%dev.quarkus.container-image.tag=latest-dev|\%dev.quarkus.container-image.tag='"${QUARKUS_CONTAINER_IMAGE_TAG}"'|g'
          src/main/resources/application.properties
   
Hier wird ein Build Image generiert:
   
    - name: Build Docker image
        run: >-
          docker build --file src/main/docker/Dockerfile.jvm
          --build-arg QUARKUS_PROFILE=${{env.QUARKUS_PROFILE}}
          --build-arg QUARKUS_DATASOURCE_USERNAME=${{env.QUARKUS_DATASOURCE_USERNAME}}
          --build-arg QUARKUS_DATASOURCE_PASSWORD=${{env.QUARKUS_DATASOURCE_PASSWORD}}
          --build-arg QUARKUS_DATASOURCE_JDBC_URL=${{env.QUARKUS_DATASOURCE_JDBC_URL}}
          -t ${{env.AZURE_IMAGE_NAME}}:${{env.AZURE_IMAGE_TAG}} .
   
Und entsprechend in meine Azure Container Registry hochgeladen:
   
   - name: Push Docker image to Azure Container Registry
        run: 
          docker push ${{env.AZURE_IMAGE_NAME}}:${{env.AZURE_IMAGE_TAG}}
   
In einem letzten Schritt habe ich versucht das ganze dann wirklich in Azure zu deployen, leider ohne Erfolg...
   
   - name: Deploy to Azure Container App
        uses: azure/CLI@v1
        with:
          inlineScript: |
            az config set extension.use_dynamic_install=yes_without_prompt
            az containerapp update -n d-ca-blog-jd-dev -g d-rg-blog-jd --image jessicasblog.azurecr.io/blog_backend_jd:DEV
            az containerapp revision restart -n d-ca-blog-jd-dev -g d-rg-blog-jd --revision d-ca-blog-jd-dev--yq4nk1o
   
Das ist wohl nur eine von 20 Varianten wie ich versucht habe meine Applikation in Azure zu deployen, und "oh boy" habe ich versuche gestartet...
   
   ![image](https://user-images.githubusercontent.com/104629842/227784423-0beb4195-da1d-40ac-9f3d-9c73b105f7b4.png)

   
Needless to say, nein ich habe es leider nicht geschaft ein Deployment in Azure zu machen... Würde mich aber mächtig interessieren an was es lag.
   
   

# Revisionen
   
Ich habe mich mit den Multiple Revisions befasst in Azure. Diese sind vor allem interessant wenn man z.B auf Blue / Green Deployment setzen möchte. Das bedeutet einen Release step by step ablösen, vielleicht möchte man den Release 1.0 noch nicht komplett durch den Release 1.1 ersetzten wenn man sich noch nicht sicher ist ob dieser auch wirklich funktioniert.
   
Wenn man mit dem Multiple Mode arbeitet, hat man mehrere Revisionen die active sein können. Jede Revision repräsentiert somit eine Version unseres Images. 
   
Eine Revision hat einen eindeutigen Namen, welcher vergeben oder automatisch/random generiert werden kann:
   ![image](https://user-images.githubusercontent.com/104629842/227801234-a450b923-6546-407e-ae2a-34f6e7a0de80.png)
   
Auch kann man Bezeichner für die Revisionen verwenden, oder sogenannte Label. Diese können immer wieder angepasst werden. Hat die Test Revision heute zum Beispiel den Release 1.2, könnten wir morgen das image mit der Version 1.3 darauf laufen lassen. In dem Fall können wir für eine klare Übersicht und Struktur den Bezeichner anpassen sobald wir das Image mit der Version 1.3 deployed haben.
   ![image](https://user-images.githubusercontent.com/104629842/227801397-62ff1ca9-0167-4ef2-97de-4252dc0a4ab1.png)

Hier ein Beispiel davon wie eine Revisions URL oder Bezeichnungs URL aussehen:
   ![image](https://user-images.githubusercontent.com/104629842/227801584-948b46c2-3ccb-4844-a899-7f2c3eebda9f.png)

Wie du siehst, nimmt er für die Revisions-URL den namen der Revision selbst in die URL, und für die Bezeichnungs URL nimmt er den im Bezeichner definierten Namen in die URL. Solang die Revisionen active sind können sie auch via ihrer URL erreicht werden.
   
Der Datenverkehr ist pro aktiver Revision konfigurierbar. Damit definieren wir wie viel % des Traffics auf welche Revision weitergeleitet werden soll:
   
   ![image](https://user-images.githubusercontent.com/104629842/227801735-b26292cf-f8d6-4f6d-92da-d0f2439d120a.png)

   
Wenn du nochmal einen Blick in meine Github Action wirfst, dann siehst du dass ich direkt via Action versuche eine Revision mit spezifischen Namen neu zu starten. Ich kann mittels Github Action also ganz einfach Befehle innerhalb meiner Azure Umgebung ausführen, sogar auf Revisions.
   
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
