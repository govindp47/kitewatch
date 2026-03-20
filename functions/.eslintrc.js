module.exports = {
  root: true,
  env: {
    es2020: true,
    node: true,
  },
  extends: [
    "eslint:recommended",
  ],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    project: ["tsconfig.json"],
    sourceType: "module",
    tsconfigRootDir: __dirname,
  },
  ignorePatterns: [
    "/lib/**/*",
    ".eslintrc.js",
  ],
  rules: {
    "no-unused-vars": "off",
  },
};
