# Postchain Chromia Development Guidelines

## Build & Configuration

### Prerequisites
- **Java 21**: Required for compilation and runtime
- **Maven 3.x**: Build system
- **Kotlin 2.0.0**: Primary programming language

### Build System
This is a multi-module Maven project with the following key modules:
- `chromia-core`: Core Chromia functionality
- `chromia-infrastructure`: Infrastructure components (ICMF, ICCF, anchoring)
- `chromia-devtools`: Development and testing tools
- `directory-chain`: Directory chain implementation
- `deployment-test`: Deployment testing

### Building the Project
```bash
# Clean and compile all modules
mvn clean compile

# Build with tests
mvn clean verify

# Build without tests (faster)
mvn clean install -DskipTests

# Build specific module
mvn clean compile -pl chromia-infrastructure
```

### Key Dependencies
- **Postchain 3.41.0**: Core blockchain framework
- **Rell 0.15.0**: Rell programming language support
- **JOOQ**: Database access layer
- **Web3j**: Ethereum integration
- **EIF (Ethereum Integration Framework)**: Cross-chain functionality

## Testing Configuration

### Testing Framework Stack
- **JUnit 5**: Primary testing framework
- **Mockito-Kotlin**: Mocking framework
- **AssertK**: Kotlin-friendly assertions
- **Awaitility**: Asynchronous testing support
- **Testcontainers**: Integration testing with containers

### Running Tests
```bash
# Run all tests
mvn test

# Run specific module tests
mvn test -pl chromia-infrastructure

# Run integration tests
mvn failsafe:integration-test

# Run specific test class
mvn test -Dtest=IccfValidationTest

# Run with debug output
mvn test -X
```

### Test Structure
Tests are organized in `src/test/kotlin` with the same package structure as source code:
- **Unit tests**: Simple JUnit 5 tests with `@Test` annotation
- **Integration tests**: Suffix `IT` (e.g., `IccfIT.kt`, `IcmfSenderIT.kt`)
- **Test resources**: Located in `src/test/resources` including Rell test code

### Creating New Tests
Example test structure:
```kotlin
package net.postchain.d1.yourmodule

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class YourModuleTest {
    
    @Test
    fun `test description in backticks`() {
        // Arrange
        val mockService = mock<YourService>()
        
        // Act
        val result = mockService.performAction()
        
        // Assert
        assertThat(result).isEqualTo(expectedValue)
        verify(mockService).performAction()
    }
}
```

### Test Dependencies Resolution
Before running tests, Maven must resolve all dependencies. If tests fail with artifact resolution errors:
1. Ensure internet connectivity
2. Run `mvn dependency:resolve` first
3. Check if parent POM dependencies are accessible

## Code Style & Development Practices

### Kotlin Code Conventions
- **Indentation**: 4 spaces (project-specific, differs from Kotlin standard)
- **Code style**: Obsolete IntelliJ IDEA style as specified in parent POM
- **Line length**: No strict limit observed, but keep reasonable

### Naming Conventions
- **Constants**: SCREAMING_SNAKE_CASE (e.g., `QUERY_ICMF_GET_MESSAGES_AT_HEIGHT`)
- **Classes**: PascalCase (e.g., `IcmfSenderGTXModule`)
- **Functions/Variables**: camelCase (e.g., `initializeContext`, `messageQueryLimit`)
- **Packages**: lowercase with dots (e.g., `net.postchain.d1.icmf`)

### Documentation Standards
- **KDoc**: Extensive documentation for public APIs
- **Query documentation**: Document query signatures and return types in comments
- **TODO comments**: Mark incomplete implementations clearly

### Error Handling
- Use `UserMistake` for user-facing errors with descriptive messages
- Use `TransactionIncorrect` for transaction validation failures
- Prefer explicit null checks with Elvis operator (`?:`)

### GTX Module Patterns
When implementing GTX modules:
```kotlin
open class YourGTXModule : SimpleGTXModule<YourModuleContext>(
    YourModuleContext(),
    mapOf(/* operations */),
    mapOf(/* queries */)
), PostchainContextAware, MetadataProvider {

    override fun getMetadata() = GTXModuleMetadata(
        operations = mapOf(/* operation metadata */),
        queries = mapOf(/* query metadata */)
    )

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        // Initialize module-specific context
    }
}
```

### Database Operations
- Use JOOQ for type-safe database queries
- Initialize database operations in `initializeDB(ctx: EContext)`
- Follow the repository pattern for data access

### Testing Patterns
- Use descriptive test names with backticks
- Follow Arrange-Act-Assert pattern
- Mock external dependencies with Mockito-Kotlin
- Use assertk for assertions
- Use `assertThrows` for exception testing
- Use `assertDoesNotThrow` for positive testing

## Project-Specific Information

### Key Frameworks
- **ICMF (Inter-Chain Message Framework)**: Message passing between chains
- **ICCF (Inter-Chain Communication Framework)**: Cross-chain communication
- **Anchoring**: Block anchoring mechanisms for security
- **GTX**: Generic Transaction framework

### Configuration
- Configuration uses GTV (Generic Type Values) format
- Module configurations are nested under module names (e.g., `icmf.sender`)
- Default values should be provided with fallbacks

### Rell Integration
- Rell code is compiled during Maven build process
- Test Rell code goes in `src/test/resources/net/postchain/d1/rell/src`
- Production Rell code is sourced from directory-chain module

### Development Environment
- IntelliJ IDEA recommended with Kotlin plugin
- Ensure Kotlin code style is set to "Obsolete" to match project settings
- Use Maven for dependency management, not Gradle

### Common Patterns
- Factory methods for creating providers and managers
- Context objects for module state management
- Extension points for blockchain functionality
- Validation with early returns and descriptive error messages

## Debugging Tips

### Common Issues
1. **Artifact resolution failures**: Run `mvn dependency:resolve` first
2. **Test compilation errors**: Ensure all test dependencies are in classpath
3. **Rell compilation issues**: Check Rell source paths in Maven configuration
4. **Database connection issues**: Verify database configuration in test resources

### Logging
- Use `kotlin-logging` for structured logging
- Log4j2 configuration available for testing
- Add debug logging to understand complex transaction flows

### Performance Considerations
- Database queries should be optimized for large datasets
- Use message query limits to prevent memory issues
- Consider async patterns for cross-chain operations