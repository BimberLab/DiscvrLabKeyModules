module.exports = {
    globals: {
        LABKEY: {},
    },
    moduleFileExtensions: ['ts', 'tsx', 'js'],
    moduleNameMapper: {
        '\\.(css)$': '<rootDir>/src/client/test/styleMock.ts',
    },
    roots: ['<rootDir>'],
    setupFilesAfterEnv: [
        './src/client/test/jest.setup.ts'
    ],
    testEnvironment: 'jsdom',
    testPathIgnorePatterns: [
        '/node_modules/'
    ],
    testRegex: '(\\.(spec))\\.(ts|tsx)$',
    transform: {
        '^.+\\.tsx?$': [
            'ts-jest',
            {
                tsconfig: 'node_modules/@labkey/build/webpack/tsconfig.json',
            }
        ],
    },
};