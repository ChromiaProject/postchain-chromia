# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System and Commands

This is a Maven-based Kotlin project for Chromia blockchain infrastructure.

### Build Commands
- `mvn clean compile` - Compile the project
- `mvn clean test` - Run unit tests
- `mvn clean verify` - Run unit tests and integration tests
- `mvn clean install` - Build and install to local repository
- `mvn --activate-profiles ci clean verify` - Build with CI profile (treats warnings as errors)
- `mvn --activate-profiles no-unit-tests clean verify` - Skip unit tests, run only integration tests

### Test Commands
- `mvn test` - Run unit tests only
- `mvn failsafe:integration-test` - Run integration tests only
- `mvn test -Dtest=ClassName` - Run specific test class
- `mvn test -Dtest=ClassName#methodName` - Run specific test method

### Profile-Specific Commands
- `mvn -Dlocal=true` - Use local directory-chain source (activates d1-local profile)
- `mvn --activate-profiles coverage` - Generate code coverage reports

## Project Architecture

This is a multi-module Maven project with the following key modules:

### Core Modules
- **chromia-core**: Core interfaces and API definitions for cluster management and node management
- **chromia-infrastructure**: Main infrastructure components including anchoring, ICMF, ICCF, and hybrid compute
- **directory-chain**: Directory blockchain components (separate dependency)
- **chromia-devtools**: Development and testing utilities

### Additional Modules
- **deployment-test**: Integration tests with Docker containers
- **docker-images**: Docker image build configurations
- **coverage-report-aggregate**: Aggregated code coverage reports

### Key Technologies and Dependencies
- **Language**: Kotlin 2.0.0 targeting JVM 21
- **Postchain**: Version 3.41.0 - the underlying blockchain framework
- **Rell**: Version 0.14.15 - smart contract language for Chromia
- **Client**: Postchain client 3.34.2
- **Web3j**: 4.12.0 for Ethereum integration
- **Testing**: JUnit 5, AssertK, Mockito Kotlin, Testcontainers

## Key Components

### Anchoring System
Located in `chromia-infrastructure/src/main/kotlin/net/postchain/d1/anchoring/`:
- Handles cross-chain anchoring operations
- Supports EVM integration via Web3j
- Includes batch and multi-op anchoring strategies

### Inter-Chain Communication Framework (ICCF)
Located in `chromia-infrastructure/src/main/kotlin/net/postchain/d1/iccf/`:
- Enables communication between different chains
- Provides proof transaction material building

### Inter-Chain Message Framework (ICMF)
Located in `chromia-infrastructure/src/main/kotlin/net/postchain/d1/icmf/`:
- Message passing between chains within and across clusters
- Supports both intra-cluster and inter-cluster communication
- Queue management for message delivery

### Hybrid Compute
Located in `chromia-infrastructure/src/main/kotlin/net/postchain/hybridcompute/`:
- Computational framework for Chromia
- Database operations and synchronization
- Special transaction extensions

## Development Environment

### Database Requirements
Tests require PostgreSQL. Integration tests use Testcontainers to spin up PostgreSQL automatically.

### Environment Variables for Tests
- `D1_SOURCE`: Points to directory-chain source code (automatically set by Maven)
- `CHR_DB_URL`: Database URL for Chromia components
- `POSTCHAIN_DB_URL`: Database URL for Postchain components

### Rell Development
Rell smart contracts are located in various directories:
- Test contracts: `src/test/resources/net/postchain/d1/rell/src/`
- Infrastructure Rell: `rell/src/`
- Deployment test Rell: `deployment-test/rell/src/`

Rell compilation is handled automatically by the `rell-maven-plugin` during builds.

### Docker Integration
The project includes Docker image generation via Jib plugin and extensive Docker-based integration testing.

## Common Development Tasks

### Running Integration Tests
Integration tests use Docker containers and require Docker to be available:
```bash
mvn clean verify  # Includes both unit and integration tests
```

### Working with Rell Code
Rell code is compiled automatically during Maven builds. Generated client code is placed in `target/generated-sources/`.

### Adding New Tests
- Unit tests: Place in `src/test/kotlin/` 
- Integration tests: Use `IT` suffix in the class name
- Rell integration tests: Use the rell-maven-plugin integration-test goal

### Database Testing
Most tests that require database use embedded H2 or Testcontainers PostgreSQL. Check existing test classes for patterns.