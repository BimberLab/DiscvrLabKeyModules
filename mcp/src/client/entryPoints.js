module.exports = {
    apps: [{
        name: 'mcpChat',
        title: 'MCP Chat',
        permissionClasses: ['org.labkey.api.security.permissions.ReadPermission'],
        path: './src/client/Chat'
    }]
};
