# Assistant Service

Application Spring Boot with IBM Watson Assistant service.

This service belong to MLGIA.

This service is configured to start in port 8083. It connect to IBM Assistant Service, so it's required set the credentials to access to it.

API exposed:
[POST] /assistant
- IN MessageDTO Message
- OUT MessageDTO Message


### Build Docker image
```
mvn clean package docker:build
```
