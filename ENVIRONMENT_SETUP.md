# Environment Configuration

This application uses environment variables for sensitive and deployment-specific configuration.

## Setup

1. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. Update the `.env` file with your actual values:
   - Set your email credentials for `EMAIL_USERNAME` and `EMAIL_PASSWORD`
   - Generate a new JWT secret for production (recommended)
   - Adjust other settings as needed for your environment

## Environment Variables

### Database Configuration
- `DB_URL`: Database connection URL (default: jdbc:h2:mem:testdb)
- `DB_USERNAME`: Database username (default: sa)
- `DB_PASSWORD`: Database password (default: password)

### Server Configuration
- `SERVER_PORT`: Port for the application server (default: 8080)

### File Upload Configuration
- `FILE_UPLOAD_DIR`: Directory for uploaded files (default: uploads)
- `MAX_FILE_SIZE`: Maximum file size (default: 10MB)
- `MAX_REQUEST_SIZE`: Maximum request size (default: 10MB)

### JWT Configuration
- `JWT_SECRET`: Secret key for JWT token signing (required)
- `JWT_EXPIRATION_MS`: Token expiration time in milliseconds (default: 86400000 = 24 hours)

### Email Configuration
- `EMAIL_HOST`: SMTP server host (default: smtp.gmail.com)
- `EMAIL_PORT`: SMTP server port (default: 587)
- `EMAIL_USERNAME`: Email username for sending emails
- `EMAIL_PASSWORD`: Email password or app password
- `EMAIL_FROM`: From email address (default: noreply@filesharingapp.com)
- `EMAIL_FROM_NAME`: From name (default: File Sharing App)

### Email Verification Settings
- `VERIFICATION_CODE_LENGTH`: Length of verification codes (default: 6)
- `VERIFICATION_EXPIRY_MINUTES`: Code expiry time in minutes (default: 15)
- `VERIFICATION_MAX_ATTEMPTS`: Maximum verification attempts (default: 3)
- `VERIFICATION_MAX_CODES_PER_HOUR`: Rate limit for code generation (default: 5)

## Security Notes

- Never commit the `.env` file to version control
- Use strong, unique values for `JWT_SECRET` in production
- Use app passwords for Gmail instead of your regular password
- Consider using a proper database (PostgreSQL, MySQL) in production instead of H2

## How It Works

The application automatically loads environment variables from the `.env` file on startup using:
- **dotenv-java library**: Reads the `.env` file and makes variables available
- **EnvironmentConfig**: Spring configuration that loads variables into the application context
- **Automatic log cleanup**: Removes old log files on startup (keeps only the 10 most recent)

## Log Management

The application automatically:
- Creates a `logs/` directory if it doesn't exist
- Cleans up old log files on startup (keeps only 10 most recent files)
- Logs the cleanup process for transparency

## Production Deployment

For production deployments, set these environment variables through your deployment platform (Docker, Kubernetes, cloud providers) instead of using a `.env` file.