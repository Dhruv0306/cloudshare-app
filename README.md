# File Sharing App

A secure, full-stack file sharing application built with Spring Boot (backend) and React (frontend), featuring comprehensive email verification, advanced security measures, complete file sharing system with token-based access, email notifications, and robust testing coverage.

## Features

### Core Functionality
- **Secure User Registration**: Email verification required for account activation
- **JWT Authentication**: Token-based authentication with secure session management
- **File Management**: Upload, download, list, and delete files with user isolation
- **File Sharing System**: Share files with secure tokens, permissions, and access controls
- **Email Verification**: 6-digit verification codes with rate limiting and expiration
- **Password Security**: Real-time password strength validation and requirements
- **File Security**: Path traversal protection and UUID-based file naming

### File Sharing Features (Backend Complete)
- **Secure Share Links**: UUID-based tokens for unpredictable, secure file access
- **Permission Control**: View-only or download permissions for shared files  
- **Access Limits**: Optional maximum access count and expiration dates
- **Access Tracking**: Comprehensive logging of all share access attempts with IP and user agent
- **Share Management**: Create, revoke, and update permissions for file shares
- **Email Notifications**: Automated email notifications for share recipients with delivery tracking
- **Cleanup Operations**: Automated cleanup of expired and inactive shares
- **Security Validation**: Token validation with expiration and permission checks
- **Rate Limiting**: IP-based rate limiting to prevent abuse and suspicious activity detection
- **Access Analytics**: Detailed statistics and reporting for share usage patterns
- **Audit Trail**: Complete logging of all share operations for security compliance

### Advanced Security
- **Rate Limiting**: Protection against brute force attacks and spam
- **Account Status Management**: Pending, verified, and suspended account states
- **Security Auditing**: Comprehensive logging of authentication and file operations
- **Input Validation**: Server-side validation for all user inputs
- **CORS Protection**: Secure cross-origin resource sharing configuration
- **Email Rate Limiting**: Maximum 5 verification codes per hour per user
- **Password Strength Analysis**: Real-time password strength scoring and requirements
- **Admin Maintenance**: Secure admin endpoints for system maintenance
- **Share Security**: Token-based access with IP tracking and access limits

### User Experience
- **Responsive Design**: Modern, mobile-friendly React interface
- **Real-time Feedback**: Password strength indicators and form validation
- **Error Handling**: Comprehensive error messages and user guidance
- **Loading States**: Visual feedback for all async operations
- **Accessibility**: Screen reader compatible with proper ARIA labels

## Technology Stack

### Backend
- **Java 17** with Spring Boot 3.5.6
- **Spring Security 6** with JWT authentication and rate limiting
- **Spring Data JPA** with H2/MySQL support and custom queries
- **Spring Mail** for email verification system
- **JWT (jsonwebtoken 0.11.5)** for secure token management
- **BCrypt** password encryption
- **Spring Validation** for comprehensive input validation
- **Dotenv Java 3.0.0** for environment configuration
- **Maven** build system with JaCoCo 0.8.11 coverage
- **Logback** structured logging with file rotation
- **Testcontainers** for integration testing

### Frontend
- **React 18.2.0** with modern hooks and context
- **Axios 1.4.0** for HTTP client with interceptors
- **CSS3** with responsive design and accessibility features
- **Jest & React Testing Library** for comprehensive testing (41 tests, 57.94% coverage)
- **ESLint** for code quality and consistency
- **React Scripts 5.0.1** for build tooling and development server

### Development & DevOps
- **GitHub Actions** CI/CD pipeline with comprehensive testing
- **Trivy** security vulnerability scanning
- **JaCoCo 0.8.11** code coverage reporting (backend)
- **Maven Surefire 3.0.0-M9** test reporting
- **Environment-based configuration** with .env support
- **H2 Console** for development database management
- **Spring Boot DevTools** for development productivity

## Current Implementation Status

### ✅ Completed Features
- **User Authentication System**: Registration, login, JWT tokens with comprehensive security
- **Email Verification**: 6-digit codes with rate limiting and expiration management
- **File Management**: Upload, download, list, delete with user isolation and security
- **File Sharing Backend**: Complete service layer with entities, repositories, and security controls
- **Share Access Control**: Token validation, permission management, and access tracking
- **Email Notification System**: Automated notifications with delivery tracking and retry mechanisms
- **Security Framework**: JWT authentication, IP-based rate limiting, audit logging, and suspicious activity detection
- **Testing Suite**: 566+ backend tests, 96 frontend tests with comprehensive coverage
- **CI/CD Pipeline**: GitHub Actions with automated testing and security scanning

### 🚧 In Progress / Planned
- **File Sharing API Endpoints**: REST controllers for share operations
- **File Sharing Frontend**: React components for share management and public access
- **Share Analytics Dashboard**: Usage statistics and reporting interface
- **Advanced Security**: Enhanced rate limiting and monitoring features

## Getting Started

### Prerequisites
- Java 17 or higher (tested with Java 21)
- Node.js 16 or higher
- npm or yarn

### Running the Backend

1. Navigate to the project root directory
2. Run the Spring Boot application:
   ```bash
   ./mvnw spring-boot:run
   ```
   Or on Windows:
   ```cmd
   mvnw.cmd spring-boot:run
   ```

The backend will start on `http://localhost:8080`

### Running the Frontend

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the React development server:
   ```bash
   npm start
   ```

The frontend will start on `http://localhost:3000`

## API Endpoints

### Authentication & User Management
- `POST /api/auth/signup` - Register new user (requires email verification)
- `POST /api/auth/signin` - Login user (requires verified account)
- `POST /api/auth/verify-email` - Verify email with 6-digit code
- `POST /api/auth/resend-verification` - Resend verification code (rate limited)
- `POST /api/auth/check-password-strength` - Validate password strength
- `GET /api/auth/user-email` - Get current user's email

### File Management (Requires Authentication & Verification)
- `POST /api/files/upload` - Upload file (max 10MB, user isolation)
- `GET /api/files` - List user's files with metadata
- `GET /api/files/{id}` - Get specific file details
- `GET /api/files/download/{fileName}` - Download user's file
- `DELETE /api/files/{id}` - Delete user's file

### File Sharing (Backend Implementation Complete)
- **Core Services**: FileSharingService and ShareAccessService with comprehensive functionality
- **Data Models**: FileShare, ShareAccess, and ShareNotification entities with proper indexing
- **Repository Layer**: Custom JPA queries for share management, analytics, and reporting
- **Share Operations**: Create, validate, revoke, cleanup, and permission management
- **Access Control**: Permission-based validation (VIEW_ONLY, DOWNLOAD) with real-time checks
- **Security Features**: Token validation, expiration checks, access limits, and rate limiting
- **Access Analytics**: Detailed statistics, usage patterns, and suspicious activity detection
- **Audit Trail**: Comprehensive logging of all share operations with IP and user agent tracking

**Note**: REST API endpoints and frontend components are planned for future implementation.

### System & Maintenance
- `GET /api/test/logs` - Test logging functionality (development)
- `POST /api/admin/maintenance/cleanup-verifications` - Clean up expired verification codes (admin only)
- `POST /api/admin/maintenance/cleanup-logs` - Clean up old log files (admin only)

## Configuration

### Environment Setup
The application uses environment variables for configuration. Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

### Key Configuration Areas

#### File Upload Settings
- **Maximum file size**: 10MB (configurable via `MAX_FILE_SIZE`)
- **Upload directory**: `uploads/` (configurable via `FILE_UPLOAD_DIR`)
- **Supported formats**: All file types with security validation
- **File naming**: UUID prefix to prevent conflicts

#### Database Configuration
- **Default**: H2 in-memory database for development
- **Production**: MySQL/PostgreSQL support via environment variables
- **H2 Console**: Available at `http://localhost:8080/h2-console`
- **Connection**: `jdbc:h2:mem:testdb` (sa/password)

#### Email Verification System
- **SMTP Configuration**: Gmail by default (configurable)
- **Verification Codes**: 6-digit numeric codes
- **Code Expiry**: 15 minutes (configurable)
- **Rate Limiting**: 5 codes per hour per user
- **Max Attempts**: 3 verification attempts per code

#### Security Configuration
- **JWT Secret**: Base64-encoded 512-bit key for HS512 algorithm
- **Token Expiry**: 24 hours (configurable)
- **Password Requirements**: Minimum 8 characters with complexity rules
- **Rate Limiting**: Built-in protection against brute force attacks

#### Logging System
- **Log Directory**: `logs/` (auto-created)
- **Log Files**: 
  - `filesharing-app.log` - General application logs
  - `security.log` - Authentication and security events
  - `file-operations.log` - File upload/download/delete operations
- **Rotation**: Daily rotation, max 10MB per file
- **Cleanup**: Automatic cleanup keeps 10 most recent files

## Usage

### Getting Started
1. **Start the backend server** (see setup instructions below)
2. **Start the frontend server** (see setup instructions below)
3. **Open your browser** to `http://localhost:3000`

### User Registration Flow
1. **Sign Up**: Create account with email and strong password
2. **Email Verification**: Check your email for a 6-digit verification code
3. **Verify Account**: Enter the code to activate your account
4. **Login**: Use your credentials to access the application

### File Management
Once logged in and verified, you can:
- **Upload Files**: Drag & drop or select files (max 10MB each)
- **View Files**: See all your uploaded files with metadata
- **Download Files**: Click download button for any of your files
- **Delete Files**: Remove files you no longer need
- **File Security**: Only you can access your files

### File Sharing (Backend Implementation Complete)
The file sharing system backend is fully implemented with comprehensive features:
- **Share Creation**: Generate secure UUID-based tokens for any file
- **Permission Control**: Set VIEW_ONLY or DOWNLOAD permissions
- **Expiration Management**: Configure optional expiration dates
- **Access Limits**: Set maximum number of accesses per share
- **Access Tracking**: Log all access attempts with IP and user agent
- **Email Notifications**: Automated notifications with delivery tracking and retry mechanisms
- **Rate Limiting**: IP-based rate limiting to prevent abuse
- **Security Monitoring**: Suspicious activity detection and automated response
- **Share Management**: Revoke, update permissions, and cleanup expired shares
- **Security Validation**: Token validation with comprehensive checks
- **Access Analytics**: Detailed statistics and usage pattern analysis
- **Audit Trail**: Complete logging of all share operations

**Note**: Frontend components and REST API endpoints for file sharing are planned for future implementation.

### Password Security System
The application includes a comprehensive password strength evaluation system:

#### Password Requirements
- **Minimum Length**: 8 characters (12+ for strong rating)
- **Character Diversity**: Must include:
  - At least one uppercase letter (A-Z)
  - At least one lowercase letter (a-z)
  - At least one number (0-9)
  - At least one special character (!@#$%^&*()_+-=[]{}|;':\"\\,.<>?/)

#### Real-time Password Analysis
- **Strength Scoring**: Dynamic scoring from 0-100
- **Strength Levels**: Weak, Medium, Strong classification
- **Visual Feedback**: Color-coded strength indicators
- **Requirement Checklist**: Real-time validation of each requirement
- **Improvement Suggestions**: Contextual tips for stronger passwords

### Email Verification Features
- **Rate Limited**: Maximum 5 verification codes per hour
- **Time Limited**: Codes expire after 15 minutes
- **Attempt Limited**: Maximum 3 attempts per code
- **Resend Option**: Request new code if needed

## CI/CD Pipeline

This project includes a comprehensive CI/CD pipeline using GitHub Actions that automatically:

- **Runs tests** on every push and pull request
- **Generates code coverage reports** using JaCoCo
- **Performs security scans** using Trivy
- **Tests both backend and frontend** components
- **Deploys automatically** after successful tests on main branch

### Pipeline Status
![CI Pipeline](https://github.com/Dhruv0306/cloudshare-app/actions/workflows/ci.yml/badge.svg)

### Workflows

1. **CI Pipeline** (`.github/workflows/ci.yml`)
   - **Backend Testing**: Maven tests with JaCoCo coverage reporting
   - **Frontend Testing**: Jest tests with 58.48% coverage
   - **Security Scanning**: Trivy vulnerability scanner
   - **Test Reporting**: JUnit test reports and coverage uploads
   - **Build Validation**: Frontend build verification
   - **Linting**: ESLint code quality checks

### Running Tests Locally

#### Backend Tests (566 Tests Passing)
```bash
# Run all backend tests
mvn test

# Run tests with coverage report
mvn test jacoco:report

# Run specific test categories
mvn test -Dtest="*EntityTest"        # Entity validation tests
mvn test -Dtest="*SecurityTest"      # JWT and security tests  
mvn test -Dtest="*ServiceTest"       # Business logic tests
mvn test -Dtest="*RepositoryTest"    # Repository and query tests
mvn test -Dtest="*ControllerTest"    # Controller integration tests

# View coverage report
open target/site/jacoco/index.html
```

#### Frontend Tests
```bash
cd frontend

# Run all frontend tests
npm test

# Run tests with coverage
npm run test:ci

# Run specific test files
npm test -- --testPathPattern=Login.test.js
npm test -- --testPathPattern=Signup.test.js

# View coverage report
open coverage/lcov-report/index.html
```

### Test Coverage Summary

#### Backend Testing (566+ Tests Passing)
- **Entity Tests**: User, FileEntity, FileShare, ShareAccess, ShareNotification validation
- **Security Tests**: JWT utilities, UserPrincipal, authentication, rate limiting
- **Service Tests**: FileService, FileSharingService, ShareAccessService, ShareNotificationService, EmailVerificationService with comprehensive scenarios
- **Repository Tests**: Custom queries for file sharing, email verification, notifications, and analytics
- **Integration Tests**: Full application context and controller testing with real database
- **Performance Tests**: Repository performance and query optimization validation
- **Coverage Areas**: 
  - ✅ JWT token lifecycle and validation
  - ✅ Path traversal attack prevention
  - ✅ File operations with ownership checks
  - ✅ Entity relationships and validation
  - ✅ Security auditing and logging
  - ✅ File sharing token generation and validation
  - ✅ Access tracking and analytics queries
  - ✅ Email verification system
  - ✅ Share cleanup and maintenance operations
  - ✅ IP-based rate limiting and abuse prevention
  - ✅ Suspicious activity detection and reporting
  - ✅ Share access statistics and analytics
  - ✅ Email notification system with delivery tracking
  - ✅ HTML email templates and retry mechanisms

#### Frontend Testing (96 Tests)
- **Component Tests**: Login, Signup, EmailVerification, AuthWrapper, PasswordStrength
- **Integration Tests**: App flow, Token authentication, Form validation, File operations
- **Coverage**: 58.48% overall with 40%+ threshold requirements
- **Coverage Areas**:
  - ✅ User authentication flows
  - ✅ Email verification process
  - ✅ Form validation and error handling
  - ✅ Password strength validation
  - ✅ Accessibility features
  - ✅ Loading states and async operations
  - ✅ Token authentication flow
  - ✅ File operations and error boundaries

### Test Quality Features
- **Isolation**: Each test runs independently with proper setup/teardown
- **Mocking**: Comprehensive mocking strategy for external dependencies
- **Security Focus**: Extensive testing of security-critical functionality
- **Accessibility**: Tests verify screen reader compatibility and ARIA labels
- **Error Scenarios**: Comprehensive error handling and edge case testing

## File Storage

Files are stored in the `uploads/` directory in the project root. Each file is given a unique UUID prefix to prevent naming conflicts.

## Development Notes

- The backend uses CORS configuration to allow requests from the React frontend
- File metadata is stored in the H2 database
- The React app uses a proxy configuration to forward API requests to the Spring Boot server
- Files are validated for security (no path traversal attacks)

## Security Architecture

### Authentication & Authorization
- **JWT Tokens**: HS512 algorithm with 512-bit secret key
- **Password Security**: BCrypt hashing with salt rounds
- **Email Verification**: Required for account activation
- **Session Management**: Secure token storage and validation
- **Account Status**: Pending/Verified/Suspended state management

### File Security
- **User Isolation**: Strict file ownership validation
- **Path Traversal Protection**: Prevents `../` directory traversal attacks
- **UUID File Naming**: Prevents filename conflicts and guessing
- **File Type Validation**: Server-side MIME type checking
- **Size Limits**: Configurable file size restrictions (default 10MB)

### Rate Limiting & Abuse Prevention
- **Email Verification**: 5 codes per hour per user
- **Login Attempts**: Protection against brute force attacks
- **Request Rate Limiting**: Configurable per-endpoint limits
- **Account Lockout**: Temporary suspension for suspicious activity

### Input Validation & Sanitization
- **Server-Side Validation**: All inputs validated before processing
- **SQL Injection Protection**: JPA/Hibernate parameterized queries
- **XSS Prevention**: Input sanitization and output encoding
- **CSRF Protection**: Spring Security CSRF tokens
- **CORS Configuration**: Restricted cross-origin access

### Security Monitoring & Auditing
- **Security Event Logging**: Dedicated `security.log` file
- **Authentication Tracking**: All login attempts and failures
- **File Operation Auditing**: Complete audit trail for file access
- **Share Access Logging**: Detailed tracking of all share access attempts
- **Suspicious Activity Detection**: Automated monitoring and alerting
- **Log Integrity**: Structured logging with timestamps and user context
- **IP-based Monitoring**: Track access patterns by IP address
- **User Agent Analysis**: Browser and client identification for security

## File Sharing System Architecture (Backend Complete)

### Core Components Implemented

#### FileSharingService
- **Share Creation**: Secure UUID-based token generation with configurable permissions
- **Share Validation**: Real-time token validation with expiration and permission checks
- **Share Management**: Create, revoke, update permissions, and cleanup operations
- **Access Control**: Comprehensive permission validation (VIEW_ONLY, DOWNLOAD)
- **Owner Validation**: Strict user ownership checks for all share operations

#### ShareAccessService  
- **Access Logging**: Comprehensive tracking of all share access attempts
- **Rate Limiting**: IP-based rate limiting to prevent abuse (configurable limits)
- **Security Monitoring**: Suspicious activity detection and automated response
- **Access Analytics**: Detailed statistics and usage pattern analysis
- **Access Validation**: Real-time permission and security checks

#### FileShare Entity
- **Unique Share Tokens**: UUID-based tokens for secure, unpredictable access
- **Permission Levels**: VIEW_ONLY (preview only) or DOWNLOAD (full access)
- **Expiration Control**: Optional expiration dates for time-limited sharing
- **Access Limits**: Optional maximum access count per share
- **Status Management**: Active/inactive status for instant revocation
- **Owner Relationship**: Linked to User entity for access control

#### ShareAccess Logging
- **Comprehensive Tracking**: Every access attempt logged with timestamp
- **IP Address Logging**: Track accessor location and patterns (45-char support for IPv6)
- **User Agent Capture**: Browser and device information for security analysis
- **Access Type Tracking**: Distinguish between VIEW and DOWNLOAD actions
- **Analytics Support**: Data foundation for usage statistics and reporting

#### ShareNotification System
- **Email Integration**: Complete automated notifications to share recipients with HTML templates
- **Delivery Tracking**: Confirmation of successful email delivery status with retry mechanisms
- **Notification History**: Complete audit trail of all notifications sent with timestamps
- **Unique Tracking IDs**: UUID-based tracking for each notification
- **Template System**: HTML email templates with file details and access links
- **Retry Logic**: Automatic retry for failed email deliveries

### Database Design (Implemented)
The file sharing system uses three interconnected tables with proper indexing:

```sql
file_shares (
  id BIGINT PRIMARY KEY,
  file_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  share_token VARCHAR(36) UNIQUE NOT NULL,
  permission ENUM('VIEW_ONLY', 'DOWNLOAD') NOT NULL,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP,
  active BOOLEAN NOT NULL,
  access_count INTEGER NOT NULL,
  max_access INTEGER
)

share_access_logs (
  id BIGINT PRIMARY KEY,
  share_id BIGINT NOT NULL,
  accessor_ip VARCHAR(45),
  user_agent CLOB,
  accessed_at TIMESTAMP NOT NULL,
  access_type ENUM('VIEW', 'DOWNLOAD') NOT NULL
)

share_notifications (
  id BIGINT PRIMARY KEY,
  share_id BIGINT NOT NULL,
  recipient_email VARCHAR(100) NOT NULL,
  sent_at TIMESTAMP NOT NULL,
  delivered BOOLEAN NOT NULL,
  notification_id VARCHAR(36) UNIQUE
)
```

**Indexes Created**:
- `idx_file_owner` on (file_id, owner_id)
- `idx_expires_at` on expires_at
- `idx_share_access` on (share_id, accessed_at)
- `idx_accessor_ip` on accessor_ip
- `idx_share_notification` on (share_id, sent_at)

### Security Features (Implemented)
- **Token-based Access**: UUID tokens for secure, unpredictable file access
- **Permission Validation**: Real-time checks for VIEW_ONLY vs DOWNLOAD permissions
- **Expiration Control**: Automatic validation of share expiration dates
- **Access Limits**: Enforcement of maximum access count per share
- **Ownership Validation**: Strict user ownership checks for share management
- **Rate Limiting**: IP-based rate limiting with configurable thresholds
- **Suspicious Activity Detection**: Automated monitoring and response to unusual patterns
- **Audit Trail**: Complete logging for security and compliance
- **Cleanup Operations**: Automated removal of expired and inactive shares

## Project Structure

```
filesharingapp/
├── src/main/java/com/cloud/computing/filesharingapp/
│   ├── config/          # Configuration classes (WebSecurity, Environment)
│   ├── controller/      # REST API controllers (Auth, File, Maintenance, LogTest)
│   ├── dto/            # Data Transfer Objects (ShareRequest, ShareResponse, etc.)
│   ├── entity/         # JPA entities (User, FileEntity, FileShare, ShareAccess, ShareNotification)
│   ├── exception/      # Custom exceptions and global handler
│   ├── repository/     # Data access layer with custom queries (6 repositories)
│   ├── security/       # Security configuration (JWT, Rate limiting, Auth filters)
│   └── service/        # Business logic layer (File, FileSharingService, Email, etc.)
├── src/test/java/      # Comprehensive test suite (32+ tests)
├── frontend/
│   ├── src/
│   │   ├── components/ # React components (Login, Signup, EmailVerification, etc.)
│   │   ├── context/    # React context providers (AuthContext)
│   │   ├── utils/      # Utility functions and validation
│   │   └── __tests__/  # Frontend test suite (41 tests, 57.94% coverage)
│   └── public/         # Static assets
├── logs/               # Application log files (security, file-operations, general)
├── uploads/            # User uploaded files
├── test-uploads/       # Test file storage
├── .kiro/              # Kiro IDE configuration and specs
└── .github/workflows/  # CI/CD pipeline with comprehensive testing
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | Database connection URL | `jdbc:h2:mem:testdb` |
| `DB_USERNAME` | Database username | `sa` |
| `DB_PASSWORD` | Database password | `password` |
| `JWT_SECRET` | JWT signing secret (Base64) | Required |
| `JWT_EXPIRATION_MS` | JWT token expiration time | `86400000` (24 hours) |
| `EMAIL_HOST` | SMTP server host | `smtp.gmail.com` |
| `EMAIL_PORT` | SMTP server port | `587` |
| `EMAIL_USERNAME` | SMTP username | Required for email |
| `EMAIL_PASSWORD` | SMTP password/app password | Required for email |
| `EMAIL_FROM` | From email address | `noreply@filesharingapp.com` |
| `EMAIL_FROM_NAME` | From name for emails | `File Sharing App` |
| `MAX_FILE_SIZE` | Maximum upload size | `10MB` |
| `MAX_REQUEST_SIZE` | Maximum request size | `10MB` |
| `FILE_UPLOAD_DIR` | Upload directory path | `uploads` |
| `SERVER_PORT` | Server port | `8080` |
| `VERIFICATION_CODE_LENGTH` | Verification code length | `6` |
| `VERIFICATION_EXPIRY_MINUTES` | Code expiry time | `15` |
| `VERIFICATION_MAX_ATTEMPTS` | Max verification attempts | `3` |
| `VERIFICATION_MAX_CODES_PER_HOUR` | Rate limit | `5` |

See `.env.example` for complete configuration options.

## Contributing

1. **Fork the repository**
2. **Create feature branch**: `git checkout -b feature/amazing-feature`
3. **Run tests**: `mvn test && cd frontend && npm test`
4. **Commit changes**: `git commit -m 'Add amazing feature'`
5. **Push to branch**: `git push origin feature/amazing-feature`
6. **Open Pull Request**

### Development Guidelines
- Follow existing code style and patterns
- Add tests for new functionality
- Update documentation as needed
- Ensure all CI checks pass
- Use meaningful commit messages

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Architecture Highlights

### Backend Architecture
- **Layered Architecture**: Controller → Service → Repository pattern
- **Dependency Injection**: Spring IoC container management
- **Exception Handling**: Global exception handler with custom exceptions
- **Data Transfer Objects**: Clean API contracts with DTOs
- **Entity Relationships**: JPA entities with proper associations
- **Security Filters**: Custom JWT and rate limiting filters

### Frontend Architecture  
- **Component-Based**: Modular React components with hooks
- **Context API**: Centralized authentication state management
- **Error Boundaries**: Graceful error handling and recovery
- **Form Validation**: Client-side validation with server confirmation
- **Responsive Design**: Mobile-first CSS with flexbox/grid
- **Accessibility**: WCAG compliant with proper ARIA labels

### Testing Strategy
- **Unit Testing**: Isolated testing with comprehensive mocking
- **Integration Testing**: Component interaction validation
- **Security Testing**: Authentication and authorization verification
- **Accessibility Testing**: Screen reader and keyboard navigation
- **Coverage Goals**: 40%+ coverage with quality over quantity focus

## Performance & Scalability

### Current Optimizations
- **Database Indexing**: Optimized queries with proper indexes
- **File Storage**: UUID-based naming prevents conflicts
- **Log Rotation**: Automatic cleanup prevents disk space issues
- **Rate Limiting**: Prevents resource exhaustion attacks
- **Connection Pooling**: Efficient database connection management

### Scalability Considerations
- **Stateless Design**: JWT tokens enable horizontal scaling
- **Database Migration**: Easy switch from H2 to production databases
- **File Storage**: Ready for cloud storage integration (S3, GCS)
- **Microservices Ready**: Modular design supports service extraction
- **Container Ready**: Prepared for Docker/Kubernetes deployment

## File Sharing Implementation Details

### Current Backend Implementation
The file sharing system backend is fully implemented with the following components:

#### Service Layer (`FileSharingService`)
- **Share Creation**: `createShare()` with UUID token generation
- **Share Validation**: `validateShareAccess()` with comprehensive checks
- **Permission Management**: `updateSharePermission()` for access control
- **Share Revocation**: `revokeShare()` and `revokeAllSharesForFile()`
- **Cleanup Operations**: `cleanupExpiredShares()` for maintenance
- **Access Tracking**: `recordShareAccess()` for audit trails

#### Data Models
- **FileShare**: Core entity with token, permissions, expiration, access limits
- **ShareAccess**: Access logging with IP, user agent, timestamp
- **ShareNotification**: Email notification tracking (framework ready)
- **SharePermission**: Enum for VIEW_ONLY and DOWNLOAD permissions
- **ShareAccessType**: Enum for VIEW and DOWNLOAD access types

#### Repository Layer
- **FileShareRepository**: Custom queries for share management
- **ShareAccessRepository**: Access logging and analytics queries  
- **ShareNotificationRepository**: Notification tracking queries

#### Security Features
- **UUID Token Generation**: Cryptographically secure share tokens
- **Permission Validation**: Real-time access control checks
- **Expiration Management**: Automatic validation of time limits
- **Access Limits**: Enforcement of maximum access counts
- **Audit Logging**: Comprehensive security event tracking

### Next Steps for Complete Implementation
1. **REST API Endpoints**: Create controllers for share operations
2. **Frontend Components**: Build React components for share management
3. **Email Integration**: Connect notification system with EmailService
4. **Public Access Routes**: Implement token-based file access
5. **Analytics Dashboard**: Create usage statistics interface

### Recent Implementation Progress
The file sharing system has made significant progress with the completion of:
- **ShareAccessService**: Complete implementation with rate limiting and security monitoring
- **Access Analytics**: Detailed statistics and reporting capabilities
- **Security Enhancements**: Suspicious activity detection and IP-based controls
- **Comprehensive Testing**: 566+ backend tests covering all sharing functionality

## Support

For questions, issues, or contributions:
- **Issues**: Use GitHub Issues for bug reports and feature requests
- **Documentation**: Check the wiki for detailed documentation  
- **Security**: Report security issues privately via email
- **Testing**: Run the comprehensive test suite before contributing
- **Code Style**: Follow existing patterns and conventions
