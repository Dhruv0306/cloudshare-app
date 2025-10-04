# File Sharing App

A full-stack file sharing application built with Spring Boot (backend) and React (frontend).

## Features

- **User Authentication**: Sign up and login with JWT tokens
- **Secure File Management**: Users can only access their own files
- **File Upload**: Upload files to the server (max 10MB)
- **File Download**: Download your uploaded files
- **File Listing**: View all your uploaded files with metadata
- **File Deletion**: Delete your own files
- **File Metadata**: Track file size, upload time, and original names
- **Responsive UI**: Clean, modern web interface
- **Security**: JWT-based authentication and authorization

## Technology Stack

### Backend
- Java 21
- Spring Boot 3.5.6
- Spring Security 6
- Spring Data JPA
- JWT Authentication (jsonwebtoken)
- H2 Database (in-memory)
- Maven

### Frontend
- React 18
- Axios for HTTP requests
- Modern CSS styling

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

### Authentication
- `POST /api/auth/signup` - Register a new user
- `POST /api/auth/signin` - Login user

### File Management (Requires Authentication)
- `POST /api/files/upload` - Upload a file (user's own)
- `GET /api/files` - Get user's files
- `GET /api/files/{id}` - Get user's file by ID
- `GET /api/files/download/{fileName}` - Download user's file
- `DELETE /api/files/{id}` - Delete user's file

## Configuration

### File Upload Settings
- Maximum file size: 10MB
- Upload directory: `uploads/` (created automatically)

### Database
- H2 in-memory database
- Console available at: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: `password`

### Logging Configuration
- Log files location: `logs/` directory
- Console logging: Enabled for development
- File logging: Automatic rotation and archival
- Security events: Tracked in separate security.log
- Test logging endpoint: `GET /api/test/logs` (for testing purposes)

## Usage

1. Start both backend and frontend servers
2. Open `http://localhost:3000` in your browser
3. **Sign up** for a new account or **login** with existing credentials
4. Once authenticated, you can:
   - Upload files using the upload form
   - View your uploaded files in the list
   - Download your files using the download button
   - Delete your files using the delete button
5. Each user can only see and manage their own files

## CI/CD Pipeline

This project includes a comprehensive CI/CD pipeline using GitHub Actions that automatically:

- **Runs tests** on every push and pull request
- **Generates code coverage reports** using JaCoCo
- **Performs security scans** using Trivy
- **Tests both backend and frontend** components
- **Deploys automatically** after successful tests on main branch

### Pipeline Status
![CI Pipeline](https://github.com/Dhruv0306/cloudshare-app/actions/workflows/ci.yml/badge.svg)
![Deploy](https://github.com/Dhruv0306/cloudshare-app/actions/workflows/deploy.yml/badge.svg)

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

```bash
# Run all tests
mvn test

# Run tests with coverage
mvn test jacoco:report

# Run specific test class
mvn test -Dtest=AuthControllerTest

# Run tests in a specific package
mvn test -Dtest="com.cloud.computing.filesharingapp.controller.*"

# Frontend tests
cd frontend
npm test

# Frontend tests with coverage
cd frontend
npm run test:ci
```

### Test Coverage

The application includes comprehensive unit tests:

- **Entity Tests**: Domain object validation and behavior
- **Service Tests**: Business logic with mocked dependencies  
- **Security Tests**: JWT token generation and validation
- **File Operations**: Upload, download, delete with security checks

### Test Categories

1. **Entity Tests**: User and FileEntity validation (8 tests)
2. **Service Tests**: FileService business logic with mocks (10 tests)
3. **Security Tests**: JWT utilities and UserPrincipal (11 tests)
4. **Total Coverage**: 29 comprehensive unit tests

### Test Results
✅ All 29 tests passing  
✅ Security validation (JWT, path traversal protection)  
✅ File operations (upload, download, delete)  
✅ Entity relationships and validation  
✅ Logging verification throughout operations

## File Storage

Files are stored in the `uploads/` directory in the project root. Each file is given a unique UUID prefix to prevent naming conflicts.

## Development Notes

- The backend uses CORS configuration to allow requests from the React frontend
- File metadata is stored in the H2 database
- The React app uses a proxy configuration to forward API requests to the Spring Boot server
- Files are validated for security (no path traversal attacks)

## Security Features

- **JWT Authentication**: Secure token-based authentication
- **Password Encryption**: BCrypt password hashing
- **File Access Control**: Users can only access their own files
- **CORS Configuration**: Proper cross-origin resource sharing setup
- **Input Validation**: Server-side validation for all inputs
- **Path Traversal Protection**: Secure file path handling

## Logging Features

- **Comprehensive Logging**: Detailed logging throughout the application
- **Separate Log Files**: 
  - `logs/filesharing-app.log` - General application logs
  - `logs/security.log` - Authentication and security events
  - `logs/file-operations.log` - File upload/download/delete operations
- **Log Rotation**: Automatic log rotation (daily, max 10MB per file)
- **Configurable Levels**: Different log levels for development and production
- **Security Auditing**: Track all login attempts, file operations, and access violations

## Future Enhancements

- File sharing via secure links with expiration
- File preview functionality for images and documents
- Drag and drop upload interface
- Upload progress bars
- File categorization and search functionality
- Persistent database (PostgreSQL/MySQL)
- Cloud storage integration (AWS S3, Google Cloud Storage)
- File versioning and history
- User roles and permissions
- Email notifications for file sharing
