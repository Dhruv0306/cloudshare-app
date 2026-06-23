# Testing Strategy & Test Plan

To maintain stability, prevent regressions, and verify security controls, CloudShare employs a strict multi-layered automated testing pipeline.

---

## 1. The Testing Pyramid

CloudShare aligns with the standard software testing pyramid, focusing heavily on fast, isolated unit tests, backed by integration tests using real backing services, and light end-to-end API validations.

```
       / \
      / E \   <-- End-to-End API / Contract Tests (10%)
     /  I  \  <-- Integration Tests with Testcontainers (20%)
    /   U   \ <-- Unit Tests (JUnit 5 + Mockito) (70%)
   /_________\
```

---

## 2. Unit Testing (JUnit 5 + Mockito)

Unit tests focus on validating core business logic in isolation. All external dependencies (Database Repositories, Redis Clients, S3 Storage adapters, ClamAV connectors) are stubbed or mocked using **Mockito**.

*   **Frameworks:** JUnit 5 (Jupyter), Mockito, AssertJ.
*   **Execution Criteria:** Must run in memory in < 1 second per test class.
*   **Target Coverage:** Minimum 80% line coverage for services and utilities.

### Example Unit Test Case (File Ownership Validation)
```java
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileServiceImpl fileService;

    @Test
    void deleteFile_UserNotOwner_ThrowsAccessDeniedException() {
        // Arrange
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        FileMetadata metadata = new FileMetadata();
        metadata.setId(fileId);
        metadata.setOwnerId(ownerId);

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(metadata));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            fileService.deleteFile(fileId, attackerId);
        });
        
        verify(fileRepository, never()).delete(any());
    }
}
```

---

## 3. Integration Testing (Testcontainers & MockMvc)

Mocking the database or cache can lead to false-positive tests since mock interfaces cannot validate actual SQL syntax, database constraint violations, or Redis connection timeouts.

CloudShare uses **Testcontainers** to orchestrate transient Docker instances of **PostgreSQL** and **Redis** for integration tests, running on the developer machine and the CI pipeline.

```mermaid
flowchart LR
    TestRunner[JUnit Test Runner]
    
    subgraph Testcontainers Engine
        PG[Transient PostgreSQL Container]
        RD[Transient Redis Container]
    end

    TestRunner -->|Starts / Wipes| PG & RD
    TestRunner -->|Spring Context / Real JDBC| PG
    TestRunner -->|Spring Context / Jedis| RD
```

### 3.1 Testcontainers Abstract Configuration
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cloudshare_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

### 3.2 Web Layer Testing (`MockMvc`)
REST endpoints are validated using Spring `MockMvc` to test controller routing, request serialization, validation error triggers, and Spring Security filters (checking that invalid tokens return `401 Unauthorized`).

---

## 4. Static Code & Security Analysis

To enforce code style consistency and discover security vulnerabilities during early compilation, the Maven build incorporates these analyzers:

| Tool | Focus Area | Maven Execution Command |
| :--- | :--- | :--- |
| **Checkstyle** | Format guidelines, naming conventions, import ordering. | `mvn checkstyle:check` |
| **SpotBugs** | NullPointer dangers, resource leaks, basic logical bugs. | `mvn spotbugs:check` |
| **OWASP Dependency Check** | Scans pom.xml dependencies for known CVE vulnerabilities. | `mvn dependency-check:check` |

---

## 5. Load & Performance Testing (Gatling)

File uploads and downloads create distinct IO bottlenecks. CloudShare specifies a **Gatling** scenario to test concurrency capacity.

*   **Test Criteria:** Simulate 100 concurrent users performing:
    1.  Login and token retrieval.
    2.  Multipart upload of a 10MB random data stream.
    3.  Fetching file list.
    4.  Downloading the uploaded file.
*   **Key Performance Indicators (KPIs):**
    *   95th Percentile API Latency: `< 200ms` for API requests; `< 1500ms` for 10MB file streaming.
    *   Error Rate: `< 0.1%` under peak concurrency.
