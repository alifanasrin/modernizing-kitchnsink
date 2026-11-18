# SOLID and OOP Concept References

Quick-reference notes with Java/Spring examples and source pointers.
Note: links were written from memory and not re-fetched, so verify them before relying on them.
Examples are illustrative sketches (Java 17, Spring style); adapt and test before using.

---

## Part 1: OOP Core Concepts

| Concept | One-line definition | Java/Spring hint |
|---|---|---|
| Encapsulation | Bundle state with behaviour and hide internals behind a public API | Private fields, expose methods |
| Abstraction | Expose what something does, hide how | Interfaces / abstract classes |
| Inheritance | A subclass reuses and specialises a parent type | `extends`; prefer sparingly |
| Polymorphism | Same call, different behaviour by runtime type | Many implementations behind one interface |
| Composition | Build behaviour by holding other objects | Prefer over inheritance ("has-a" before "is-a") |

### 1.1 Encapsulation

State is private; the only way to change it is through methods that enforce the rules.

```java
public final class OtpChallenge {
    private final String codeHash;
    private final Instant expiresAt;
    private boolean consumed;

    public OtpChallenge(String codeHash, Instant expiresAt) {
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    // Callers cannot flip 'consumed' directly or skip the expiry check
    public boolean consume(String submittedHash, Instant now) {
        if (consumed || now.isAfter(expiresAt)) return false;
        if (!MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.UTF_8),
                submittedHash.getBytes(StandardCharsets.UTF_8))) return false;
        consumed = true;
        return true;
    }
}
```

### 1.2 Abstraction and Polymorphism

Callers depend on what a verifier does, not how. Runtime type decides behaviour.

```java
public interface MfaVerifier {
    boolean verify(String userId, String code);
}

public class TotpVerifier implements MfaVerifier {
    @Override public boolean verify(String userId, String code) { /* compute TOTP window */ return false; }
}

public class EmailOtpVerifier implements MfaVerifier {
    @Override public boolean verify(String userId, String code) { /* check stored hash */ return false; }
}

MfaVerifier verifier = new TotpVerifier();   // could be EmailOtpVerifier
boolean ok = verifier.verify("u1", "123456"); // polymorphic call
```

### 1.3 Inheritance (use sparingly)

Good fit: a shared skeleton with small varying steps (template method).

```java
public abstract class Notifier {
    public final void notifyUser(String userId, String message) {
        String address = resolveAddress(userId);   // varies
        deliver(address, message);                 // varies
        audit(userId);                             // shared
    }
    protected abstract String resolveAddress(String userId);
    protected abstract void deliver(String address, String message);
    private void audit(String userId) { /* log */ }
}

public class EmailNotifier extends Notifier {
    @Override protected String resolveAddress(String userId) { return "user@example.com"; }
    @Override protected void deliver(String address, String message) { /* SMTP */ }
}
```

### 1.4 Composition over inheritance

Combine behaviours by holding collaborators instead of extending classes.

```java
public class LoginService {
    private final PasswordChecker passwordChecker;
    private final MfaVerifier mfaVerifier;

    public LoginService(PasswordChecker passwordChecker, MfaVerifier mfaVerifier) {
        this.passwordChecker = passwordChecker;
        this.mfaVerifier = mfaVerifier;
    }

    public boolean login(String userId, String password, String code) {
        return passwordChecker.matches(userId, password) && mfaVerifier.verify(userId, code);
    }
}
```

### References
- Oracle Java Tutorials, "What Is an Object / Class / Inheritance / Interface": https://docs.oracle.com/javase/tutorial/java/concepts/
- Gamma, Helm, Johnson, Vlissides, *Design Patterns* (1994): origin of "favor object composition over class inheritance"; Template Method and Strategy patterns.
- Joshua Bloch, *Effective Java* (3rd ed.): Items on composition over inheritance, minimising mutability and accessibility.

---

## Part 2: SOLID

### S: Single Responsibility Principle (SRP)
A class should have one reason to change (one actor it answers to).

Violation:
```java
public class VerificationService {
    public void verifyEmail(String memberId, String code) {
        // 1. business rules  2. hashing  3. Mongo queries  4. sending email
        // four reasons to change in one class
    }
}
```

Better: split by responsibility.
```java
public class EmailVerificationService {           // rules only
    private final CodeHasher hasher;
    private final VerificationTokenRepository tokens;
    private final MailSender mailSender;
    // constructor omitted
    public boolean verify(String memberId, String code, Instant now) {
        return tokens.consumeIfValid(memberId, hasher.hash(code), now);
    }
}
public interface CodeHasher { String hash(String code); }
public interface VerificationTokenRepository { boolean consumeIfValid(String memberId, String hash, Instant now); }
public interface MailSender { void send(String to, String subject, String body); }
```

### O: Open/Closed Principle (OCP)
Add behaviour by adding code, not editing tested code.

Violation:
```java
public boolean verify(String method, String userId, String code) {
    if (method.equals("TOTP")) { /* ... */ }
    else if (method.equals("EMAIL")) { /* ... */ }
    // every new method edits this block
    return false;
}
```

Better: Spring injects all implementations; a new method is a new class.
```java
public interface MfaVerifier {
    String method();                          // "TOTP", "EMAIL", ...
    boolean verify(String userId, String code);
}

@Service
public class MfaService {
    private final Map<String, MfaVerifier> byMethod;

    public MfaService(List<MfaVerifier> verifiers) {      // Spring injects every bean
        this.byMethod = verifiers.stream()
            .collect(Collectors.toMap(MfaVerifier::method, v -> v));
    }

    public boolean verify(String method, String userId, String code) {
        MfaVerifier v = byMethod.get(method);
        return v != null && v.verify(userId, code);       // no edits when adding SMS, passkey, etc.
    }
}
```

### L: Liskov Substitution Principle (LSP)
Subtypes must honour the base contract.

Contract: `consume` returns true at most once per code.
```java
public interface OtpStore {
    /** Returns true only the first time a valid code is submitted; false afterwards. */
    boolean consume(String userId, String codeHash);
}

// Violates LSP: never invalidates, so the same code works repeatedly
public class BrokenCacheOtpStore implements OtpStore {
    @Override public boolean consume(String userId, String codeHash) {
        return cache.contains(userId, codeHash);   // postcondition weakened
    }
}

// Honours the contract (atomic delete = single use)
public class MongoOtpStore implements OtpStore {
    @Override public boolean consume(String userId, String codeHash) {
        Query q = new Query(Criteria.where("userId").is(userId).and("codeHash").is(codeHash));
        return mongoTemplate.remove(q, OtpDoc.class).getDeletedCount() == 1;
    }
}
```
Classic smell: overriding a method just to throw `UnsupportedOperationException`.

### I: Interface Segregation Principle (ISP)
Prefer small role-specific interfaces.

Violation:
```java
public interface OtpService {
    String issue(String userId);
    boolean verify(String userId, String code);
    void purgeExpired();
    List<String> exportAuditLog();    // a verifier-only client must still depend on all of this
}
```

Better:
```java
public interface OtpIssuer   { String issue(String userId); }
public interface OtpVerifier { boolean verify(String userId, String code); }
public interface OtpCleanup  { void purgeExpired(); }

// One class may implement several roles; clients depend only on the one they use
public class MongoOtpService implements OtpIssuer, OtpVerifier, OtpCleanup { /* ... */ }
```

### D: Dependency Inversion Principle (DIP)
High-level policy depends on abstractions; details implement them. (Constructor injection is the technique; DIP is the design goal.)

Violation:
```java
public class SignupService {
    private final SmtpClient smtp = new SmtpClient("smtp.example.com");  // hard-wired detail
}
```

Better:
```java
public class SignupService {
    private final MailSender mailSender;                 // abstraction
    public SignupService(MailSender mailSender) {        // injected
        this.mailSender = mailSender;
    }
}

@Component
class SmtpMailSender implements MailSender { /* detail */ }

// Test: no real SMTP needed
MailSender fake = (to, subject, body) -> {};
SignupService svc = new SignupService(fake);
```

### References
- Robert C. Martin, "The Principles of OOD": http://butunclebob.com/ArticleS.UncleBob.PrinciplesOfOod
- Robert C. Martin, *Clean Architecture* (2017): SOLID chapters (Part III).
- Wikipedia overview, SOLID: https://en.wikipedia.org/wiki/SOLID
- Bertrand Meyer, *Object-Oriented Software Construction* (1988/1997): original Open/Closed formulation.
- Barbara Liskov and Jeannette Wing, "A Behavioral Notion of Subtyping", ACM TOPLAS (1994): formal LSP.
- Martin Fowler, "Inversion of Control Containers and the Dependency Injection pattern": https://martinfowler.com/articles/injection.html
- Spring Framework reference, Dependency Injection: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html

---

## Part 3: Common Pairings and Pitfalls

- SRP + DIP: small single-purpose classes wired through interfaces make unit testing easy (mock the repository).
- OCP + Polymorphism: the strategy pattern is the usual vehicle (see the `MfaService` example above).
- LSP guards inheritance: if substitution breaks, switch to composition.
- Do not over-apply: an interface with a single implementation and no foreseeable second one is often speculative abstraction.

## Part 4: Self-Review Checklist

- [ ] Can I name this class's single reason to change?
- [ ] Can I add a new variant without editing existing classes?
- [ ] Does every subclass honour the parent's contract (including postconditions like "single use")?
- [ ] Do callers only see the methods they need?
- [ ] Do my services depend on interfaces, injected via constructor?