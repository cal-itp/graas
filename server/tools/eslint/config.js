module.exports = {
  'env': {
    'browser': true,
    'commonjs': true,
    'es2021': true,
  },
// Extending the Google JS style guide creates 13816 errors, omitting for now
// If we'd like to do this it will require significant effort.
// 'extends': [
//   'google'
// ],
  'parserOptions': {
    'ecmaVersion': 'latest',
  },
  'rules': {
    // indent currently creates 510 errors, omitting for now
    // "indent": ["error", 4],
    "no-redeclare": "error",
    // max-len current creates 17 errors, omitting for now
    // "max-len": ["error", { "code": 128 }],
    "require-jsdoc": "off"
  },
};
