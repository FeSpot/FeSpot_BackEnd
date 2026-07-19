# FeSpot Backend

FeSpot 서비스의 Spring Boot REST API 백엔드입니다.

## Included

- Standard success/error response wrapper
- Global exception handler
- JPA `BaseEntity` with auditing
- OpenFeign QueryDSL configuration
- Swagger UI configuration
- JWT security infrastructure
- Swagger error response examples
- Basic CORS configuration

## Commands

```bash
./gradlew test
./gradlew bootRun
```

Swagger UI is available at `/swagger-ui.html`.
