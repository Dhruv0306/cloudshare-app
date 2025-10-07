# File Sharing App

A secure, full-stack file sharing application built with Spring Boot (backend) and React (frontend), featuring comprehensive email verification, advanced security measures, file sharing capabilities, and robust testing coverage.

## Features

### Core Functionality
- **Secure User Registration**: Email verification required for account activation
- **JWT Authentication**: Token-based authentication with secure session management
- **File Management**: Upload, download, list, and delete files with user isolation
- **File Sharing System**: Share files with secure tokens, permissions, and access controls
- **Email Verification**: 6-digit verification codes with rate limiting and expiration
- **Password Security**: Real-time password strength validation and requirements
- **File Security**: Path traversal protection and UUID-based file naming

### File Sharing Features
- **Secure Share Links**: UUID-based tokens for unpredictable, secure file access
- **Permission Control**: View-only or download permissions for shared files
- **Access Limits**: Optional maximum access count and expiration dates
- **Access Tracking**: Comprehensive logging of all share access attempts
- **Email Notifications**: Automated notifications to recipients with share links
- **Usage Analytics**: Detailed statistics on share usage and access patterns
- **Security Monitoring**: IP-based access tracking and suspicious activity detection

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
- **Spring Mail** for email verification and notifications
- **JWT (jsonwebtoken 0.11.5)** for secure token management
- **BCrypt** password encryption
- **Spring Validation** for comprehensive input validation
- **Dotenv Java** for environment configuration
- **Maven** build system with JaCoCo coverage
- **Logback** structured logging with file rotation
- **Testcontainers** for integration testing

### Frontend
- **React 18** with modern hooks and context
- **Axios** for HTTP client with interceptors
- **CSS3** with responsive design and accessibility features
- **Jest & React Testing Library** for comprehensive testing (41 tests, 57.94% coverage)
- **ESLint** for code quality
- **React Scripts 5.0.1** for build tooling

### Development & DevOps
- **GitHub Actions** CI/CD pipeline with MySQL service
- **Trivy** security vulnerability scanning
- **JaCoCo** code coverage reporting (backend)
- **Maven Surefire** test reporting
- **Environment-based configuration** with .env support
- **H2 Console** for development database management

## Getting Started

### Prerequisites
- Java 21 or higher
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

### File Sharing (Requires Authentication & Verification)
- `POST /api/shares/create` - Create secure share link for a file
- `GET /api/shares` - List user's active file shares
- `GET /api/shares/{shareToken}` - Access shared file via token
- `GET /api/shares/{shareToken}/download` - Download shared file
- `DELETE /api/shares/{id}` - Revoke file share
- `GET /api/shares/{id}/analytics` - Get share usage statistics
- `POST /api/shares/{id}/notify` - Send email notification to recipients

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

### File Sharing
Advanced sharing capabilities for secure file distribution:
- **Create Share Links**: Generate secure, token-based links for any file
- **Set Permissions**: Choose view-only or download access for recipients
- **Configure Expiration**: Set optional expiration dates for shares
- **Limit Access**: Set maximum number of accesses per share
- **Track Usage**: Monitor who accessed your shares and when
- **Email Notifications**: Send share links directly to recipients via email
- **Revoke Access**: Instantly disable share links when needed
- **Analytics Dashboard**: View detailed statistics on share usage patterns

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
   - Backend tests with MySQL service
   - Frontend tests with coverage
   - Security vulnerability scanning
   - Test report generation

2. **Deployment** (`.github/workflows/deploy.yml`)
   - Automatic deployment after successful CI
   - Artifact generation for production

3. **Status Badges** (`.github/workflows/status-badges.yml`)
   - Updates build and coverage badges

### Running Tests Locally

#### Backend Tests
```bash
# Run all backend tests
mvn test

# Run tests with coverage report
mvn test jacoco:report

# Run specific test categories
mvn test -Dtest="*EntityTest"        # Entity validation tests
mvn test -Dtest="*SecurityTest"      # JWT and security tests  
mvn test -Dtest="*ServiceTest"       # Business logic tests

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

#### Backend Testing (32+ Tests)
- **Entity Tests** (11 tests): User, FileEntity, FileShare, ShareAccess, ShareNotification validation
- **Security Tests** (11 tests): JWT utilities, UserPrincipal, authentication, rate limiting
- **Service Tests** (10+ tests): FileService with mocked dependencies
- **Repository Tests**: Custom queries for file sharing and analytics
- **Coverage Areas**: 
  - ✅ JWT token lifecycle and validation
  - ✅ Path traversal attack prevention
  - ✅ File operations with ownership checks
  - ✅ Entity relationships and validation
  - ✅ Security auditing and logging
  - ✅ File sharing token generation and validation
  - ✅ Access tracking and analytics queries
  - ✅ Email notification tracking

#### Frontend Testing (41 Tests)
- **Component Tests**: Login (5), Signup (10), EmailVerification (16)
- **Integration Tests**: App flow (3), Password strength (3), Form validation (4)
- **Coverage**: 57.94% overall with 40%+ threshold requirements
- **Coverage Areas**:
  - ✅ User authentication flows
  - ✅ Email verification process
  - ✅ Form validation and error handling
  - ✅ Password strength validation
  - ✅ Accessibility features
  - ✅ Loading states and async operations

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

## File Sharing System Architecture

### Core Components

#### FileShare Entity
- **Unique Share Tokens**: UUID-based tokens for secure, unpredictable access
- **Permission Levels**: VIEW_ONLY (preview only) or DOWNLOAD (full access)
- **Expiration Control**: Optional expiration dates for time-limited sharing
- **Access Limits**: Optional maximum access count per share
- **Status Management**: Active/inactive status for instant revocation

#### ShareAccess Logging
- **Comprehensive Tracking**: Every access attempt logged with timestamp
- **IP Address Logging**: Track accessor location and patterns
- **User Agent Capture**: Browser and device information for security
- **Access Type Tracking**: Distinguish between view and download actions
- **Analytics Support**: Data foundation for usage statistics and reporting

#### ShareNotification System
- **Email Integration**: Automated notifications to share recipients
- **Delivery Tracking**: Confirmation of successful email delivery
- **Notification History**: Complete audit trail of all notifications sent
- **Unique Tracking IDs**: UUID-based tracking for each notification

### Database Design
The file sharing system uses three interconnected tables:

```sql
file_shares (
  id, file_id, owner_id, share_token, permission, 
  created_at, expires_at, active, access_count, max_access
)

share_access_logs (
  id, share_id, accessor_ip, user_agent, 
  accessed_at, access_type
)

share_notifications (
  id, share_id, recipient_email, sent_at, 
  delivered, notification_id
)
```

### Security Features
- **Token-based Access**: No user authentication required for share access
- **Path Traversal Protection**: Secure file serving with validation
- **Rate Limiting**: Protection against abuse of sharing endpoints
- **Access Validation**: Real-time checks for expiration and limits
- **Audit Trail**: Complete logging for security and compliance

## Project Structure

```
filesharingapp/
├── src/main/java/com/cloud/computing/filesharingapp/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST API controllers
│   ├── dto/            # Data Transfer Objects
│   ├── entity/         # JPA entities (User, FileEntity, FileShare, ShareAccess, ShareNotification)
│   ├── exception/      # Custom exceptions
│   ├── repository/     # Data access layer with custom queries
│   ├── security/       # Security configuration (JWT, Rate limiting)
│   └── service/        # Business logic layer
├── frontend/
│   ├── src/
│   │   ├── components/ # React components (Login, Signup, EmailVerification, etc.)
│   │   ├── context/    # React context providers (AuthContext)
│   │   └── utils/      # Utility functions and validation
│   └── public/         # Static assets
├── logs/               # Application log files (security, file-operations, general)
├── uploads/            # User uploaded files
├── test-uploads/       # Test file storage
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

## Support

For questions, issues, or contributions:
- **Issues**: Use GitHub Issues for bug reports and feature requests
- **Documentation**: Check the wiki for detailed documentation  
- **Security**: Report security issues privately via email
- **Testing**: Run the comprehensive test suite before contributing
- **Code Style**: Follow existing patterns and conventions
