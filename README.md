# Atlas ERP

O repositório contém a aplicação original na raiz e um backend novo e independente em `backend/`. Os comandos e as instruções da aplicação original estão preservados abaixo.

## Backend novo (`backend/`) — Java 21

O backend novo usa Spring Boot, Maven, PostgreSQL e Flyway. Execute os comandos a partir de `backend/`:

```powershell
.\mvnw.cmd -B verify
```

`verify` executa os testes unitários e de integração. Os testes de integração usam PostgreSQL temporário via Testcontainers e requerem Docker em execução.

Para construir a imagem Docker localmente, também a partir de `backend/`:

```powershell
docker build -t atlas-erp-backend .
```

A execução da aplicação requer configuração externa do datasource (`DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`) e de `JWT_SECRET`. Não coloque credenciais reais em arquivos versionados. O bootstrap administrativo fica desabilitado por padrão; a configuração normal do Flyway usa somente `classpath:db/migration`.

## Aplicação original na raiz — Java 17

REST API for user management in an ERP system built with Java and Spring Boot.

 About the Project

Atlas ERP is a backend application that simulates part of an Enterprise Resource Planning system, focusing on user management with a clean architecture approach.

The project demonstrates best practices such as DTO usage, layered architecture, global exception handling, and RESTful API design.

Technologies

* Java 17
* Spring Boot
* Spring Web
* Spring Data JPA
* Maven
* H2 Database
* REST API

Architecture

The project follows a layered structure:

* Controller → handles HTTP requests
* Service → business logic
* Repository → database access
* DTO → data transfer objects
* Entity → domain model
* Exception → global error handling

Project Structure

```
atlas-erp
 ├── controller
 ├── service
 ├── repository
 ├── dto
 ├── entity
 ├── exception
 ├── config
```

How to Run

```bash
./mvnw spring-boot:run
```

API runs on:

```
http://localhost:8080
```

Endpoints

Create user

POST `/users`

```
{
  "name": "John",
  "email": "john@email.com",
  "password": "123456"
}
```

Get users

GET `/users`

Get user by id

GET `/users/{id}`

Update user

PUT `/users/{id}`

Delete user

DELETE `/users/{id}`

Purpose

This project was created to demonstrate backend development skills using Spring Boot and to serve as part of a professional portfolio.

Author

Guilherme Piccini
