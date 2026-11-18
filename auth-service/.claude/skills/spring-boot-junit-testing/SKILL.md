---
name: spring-boot-junit-testing
description: Write JUnit 5 tests for Spring Boot (unit, web layer, data/Mongo, integration) following best practices. Use when asked to write or improve Java Spring Boot test cases.
---

# Spring Boot JUnit Testing

## Workflow
1. Read the class under test (and its dependencies) before writing anything.
2. Check existing tests first: find the test class (es) for the target under `../../../src/test/java` (search by class name, e.g. `*MemberService*Test`, and grep for the method names). Read them and list which scenarios are already covered, including coverage in other test types (e.g. controller test vs. service test).
3. Pick the lightest test type that proves the behavior (see table).
4. List scenarios first: happy path, validation failures, not-found, duplicates/conflicts, exceptions, boundary values, null/empty. Remove any scenario already covered.
5. Add only missing tests. Extend the existing test class instead of creating a new one, reuse its fixtures/builders/helpers, and follow its naming style. Never recreate a test that already exists, even under a different name, and don't assert the same behavior at multiple layers without a reason.
6. Write tests, run them (`mvn test -Dtest=ClassName` or `./gradlew test --tests ClassName`), fix failures.
7. Report which scenarios were already covered, which you added, and any gaps.

## Prioritize by criticality
Spend test effort where a failure costs most. Cover in this order:
1. **Critical:** money/payments, auth and permissions, data integrity (unique constraints, duplicates, deletes), security, core business rules. Test happy path, every failure path, and edge cases.
2. **High:** main user flows, validation, error handling and exception mapping (e.g. 409/404/400), integrations with external systems.
3. **Medium:** secondary features, filtering/sorting/pagination, mapping and transformation logic.
4. **Low:** trivial getters/setters, simple pass-through methods, config. Skip or cover minimally.

When asked to test a large area, start with critical and high items, state what was deprioritized, and mention it in the final report. Mark critical tests with `@Tag("critical")` so they can run first (`mvn test -Dgroups=critical`).

## Choose the test type
| Target | Style | Annotations |
|---|---|---|
| Service / utility logic | Plain unit test, no Spring context | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` |
| Controller + JSON + validation + exception handler | Web slice | `@WebMvcTest(Controller.class)`, `@MockBean` (or `@MockitoBean` on Boot 3.4+), `MockMvc` |
| Repository / queries / indexes | Data slice | `@DataMongoTest` or `@DataJpaTest` + Testcontainers |
| Full flow across layers | Integration | `@SpringBootTest` + Testcontainers, used sparingly |

Prefer unit and slice tests. Few `@SpringBootTest` tests, since each loads the full context and is slow.

## Best practices (apply all of these)
**Principles**
- Tests are FIRST: Fast, Isolated, Repeatable, Self-validating, Timely.
- Follow the test pyramid: many unit tests, some slice tests, few integration tests.
- A test should fail for one reason only, and its failure message should point to the cause.
- Tests are documentation: a reader should understand the behavior from the name and body alone.
- No logic in tests (no loops, conditionals, or reimplementing production code to compute expected values). Use literal expected values.
- Coverage is a signal, not a goal. Prefer meaningful assertions over high percentages.
- Don't test private methods directly; test them through public behavior.
- Fix flaky tests immediately; never retry or ignore them.

**Conventions**
- JUnit 5 (`org.junit.jupiter.api`) and AssertJ (`assertThat`). No JUnit 4 imports.
- Name tests by behavior: `shouldReturnConflict_whenEmailAlreadyExists()`. Optionally group with `@Nested` and `@DisplayName`.
- Arrange / Act / Assert, separated by blank lines. One behavior per test.
- Tests must be independent and order-agnostic. No shared mutable state.
- Use test data builders or factory methods instead of repeating setup.
- Use `@ParameterizedTest` (`@ValueSource` / `@CsvSource` / `@MethodSource`) for input variations.
- Test exceptions with `assertThatThrownBy(() -> ...).isInstanceOf(X.class).hasMessageContaining(...)`.
- No `Thread.sleep`. Use Awaitility for async. Inject a `Clock` instead of calling `LocalDateTime.now()` in code under test.
- Test behavior, not implementation. Use `verify` only for meaningful side effects (save called, email sent).
- Don't mock the class under test or simple value objects. Mock collaborators at boundaries.
- Don't write tests that only assert getters/setters or framework behavior.
- Keep tests fast and deterministic. No real network, no dependence on local DB state.

## Templates

### Service unit test
```java
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository repository;
    @InjectMocks MemberService service;

    @Test
    void shouldThrowConflict_whenEmailAlreadyExists() {
        var request = new CreateMemberRequest("a@b.com", "Ann");
        when(repository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateMemberException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldSaveMember_whenRequestIsValid() {
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.create(new CreateMemberRequest("a@b.com", "Ann"));

        assertThat(result.email()).isEqualTo("a@b.com");
    }
}
```

### Controller slice test
```java
@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean MemberService service;

    @Test
    void shouldReturn200_withPagedMembers() throws Exception {
        when(service.list(1, 1)).thenReturn(new PageImpl<>(List.of(new MemberDto("1", "Ann"))));

        mockMvc.perform(get("/members").param("pageNo", "1").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Ann"));
    }

    @Test
    void shouldReturn409_onDuplicate() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateKeyException("dup"));

        mockMvc.perform(post("/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"name\":\"Ann\"}"))
                .andExpect(status().isConflict());
    }
}
```

### Repository test with Testcontainers (MongoDB)
```java
@DataMongoTest
@Testcontainers
class MemberRepositoryTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired MemberRepository repository;

    @Test
    void shouldfindById() {
        repository.save(new Member(null, "a@b.com", "Ann"));

        assertThat(repository.findById("a@b.com")).isPresent();
    }
}
```
For unique-index tests, enable `spring.data.mongodb.auto-index-creation=true` in test config and assert `DuplicateKeyException` on the second save.

### Integration test
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MemberApiIT {
    @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
    @Autowired TestRestTemplate rest;
    // exercise the real endpoint end to end
}
```

## Dependencies
`spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, MockMvc). Add `org.testcontainers:junit-jupiter`, `org.testcontainers:mongodb` (or `postgresql`), and `spring-boot-testcontainers` for DB tests. Add `spring-security-test` if security is on.

## Layout
- Mirror the main package under `../../../src/test/java`.
- Unit/slice tests: `*Test`. Integration tests: `*IT`.
- Test resources in `src/test/resources` (`application-test.properties`, JSON fixtures).

## Checklist before finishing
- No duplicate tests: every new test covers a scenario not already present.
- Tests pass and are not flaky on rerun.
- Each public method has a happy-path and at least one failure-path test.
- No unnecessary `@SpringBootTest`.
- No commented-out code, `System.out.println`, or sleeps.
- Assertions are specific (not just `assertNotNull`).