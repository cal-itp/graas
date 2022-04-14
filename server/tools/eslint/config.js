module.exports = {
  'env': {
    'browser': true,
    'commonjs': true,
    'es2021': true,
  },
  // Extending the Google JS style guide leads to thousands of errors.
  // If we'd like to do this it will require significant effort.
  // 'extends': [
  //   'google'
  // ],
  'parserOptions': {
    'ecmaVersion': 'latest',
  },
  'rules': {
    // "indent": ["error", 4],
    "no-redeclare": "error",
    // "max-len": ["error", { "code": 128 }],
    "require-jsdoc": "off"
  },
};
