module.exports = {
  extends: [
    'react-app',
    'react-app/jest'
  ],
  rules: {
    // Temporarily disable some testing-library rules for complex integration tests
    'testing-library/no-wait-for-multiple-assertions': 'warn',
    'testing-library/no-wait-for-side-effects': 'warn',
    'testing-library/no-node-access': 'error'
  }
};