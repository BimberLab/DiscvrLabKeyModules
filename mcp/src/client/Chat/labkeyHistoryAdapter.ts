import { ActionURL, Ajax } from '@labkey/api';
import type { ThreadHistoryAdapter, ThreadMessage } from '@assistant-ui/react';

interface StoredMessage {
    messageId: string;
    parentId: string | null;
    format: string;
    content: string;
}

interface GetMessagesResponse {
    messages: StoredMessage[];
}

function ajaxPost<T>(url: string, data: unknown): Promise<T> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url,
            method: 'POST',
            jsonData: data,
            success: (xhr: XMLHttpRequest) => {
                try { resolve(JSON.parse(xhr.responseText) as T); }
                catch { reject(new Error('Failed to parse response')); }
            },
            failure: (xhr: XMLHttpRequest) => {
                reject(new Error(xhr.statusText || 'Request failed'));
            },
        });
    });
}

function ajaxGet<T>(url: string): Promise<T> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url,
            method: 'GET',
            success: (xhr: XMLHttpRequest) => {
                try { resolve(JSON.parse(xhr.responseText) as T); }
                catch { reject(new Error('Failed to parse response')); }
            },
            failure: (xhr: XMLHttpRequest) => {
                reject(new Error(xhr.statusText || 'Request failed'));
            },
        });
    });
}

export function createLabKeyHistoryAdapter(remoteId: string): ThreadHistoryAdapter {
    return {
        async load() {
            if (!remoteId) return { messages: [] };

            const url = ActionURL.buildURL('mcp', 'getMessages.api') + '?remoteId=' + encodeURIComponent(remoteId);
            const data = await ajaxGet<GetMessagesResponse>(url);

            const messages = data.messages.map((m) => ({
                parentId: m.parentId ?? null,
                message: JSON.parse(m.content) as ThreadMessage,
            }));

            return { messages };
        },

        async append({ parentId, message }) {
            if (!remoteId) return;

            await ajaxPost(ActionURL.buildURL('mcp', 'appendMessage.api'), {
                remoteId,
                messageId: message.id,
                parentId: parentId ?? null,
                format: 'aui/v0',
                content: JSON.stringify(message),
            });
        },
    };
}
