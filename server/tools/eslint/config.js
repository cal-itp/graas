module.exports = {
  'env': {
    'browser': true,
    'commonjs': true,
    'es2021': true,
  },
  'extends': [
    'google',
  ],
  'parserOptions': {
    'ecmaVersion': 'latest',
  },
  'rules': {
    "indent": ["error", 4],
    "no-redeclare": "error",
    "max-len": ["error", { "code": 128 }],
    "require-jsdoc": "off"
  },
};
