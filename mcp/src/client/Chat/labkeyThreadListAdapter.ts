import React, { useMemo, type FC, type PropsWithChildren } from 'react';
import { ActionURL, Ajax } from '@labkey/api';
import {
    useThreadListItemRuntime,
    RuntimeAdapterProvider,
} from '@assistant-ui/react';
import type {
    unstable_RemoteThreadListAdapter as RemoteThreadListAdapter,
    ThreadMessage,
} from '@assistant-ui/react';
import { createAssistantStream } from 'assistant-stream';
import { createLabKeyHistoryAdapter } from './labkeyHistoryAdapter';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

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

interface ChatRow {
    remoteId: string;
    title: string | null;
    status: 'regular' | 'archived';
    modified: number | null;
}

interface ListChatsResponse {
    chats: ChatRow[];
}

// ---------------------------------------------------------------------------
// Provider — injects a per-thread history adapter
// ---------------------------------------------------------------------------

const ThreadProvider: FC<PropsWithChildren> = ({ children }) => {
    const threadListItemRuntime = useThreadListItemRuntime();
    const remoteId = threadListItemRuntime.getState().remoteId ?? '';

    const history = useMemo(
        () => createLabKeyHistoryAdapter(remoteId),
        [remoteId]
    );

    return React.createElement(
        RuntimeAdapterProvider,
        { adapters: { history }, children }
    );
};

// ---------------------------------------------------------------------------
// Adapter
// ---------------------------------------------------------------------------

export function createLabKeyThreadListAdapter(): RemoteThreadListAdapter {
    return {
        async list() {
            const [regular, archived] = await Promise.all([
                ajaxGet<ListChatsResponse>(ActionURL.buildURL('mcp', 'listChats.api')),
                ajaxGet<ListChatsResponse>(ActionURL.buildURL('mcp', 'listChats.api') + '?archived=true'),
            ]);

            const regularThreads = regular.chats
                .filter((c) => c.status === 'regular')
                .map((c) => ({
                    remoteId: c.remoteId,
                    title: c.title ?? undefined,
                    status: 'regular' as const,
                }));

            const archivedThreads = archived.chats
                .filter((c) => c.status === 'archived')
                .map((c) => ({
                    remoteId: c.remoteId,
                    title: c.title ?? undefined,
                    status: 'archived' as const,
                }));

            return { threads: [...regularThreads, ...archivedThreads] };
        },

        async initialize(threadId: string) {
            await ajaxPost(ActionURL.buildURL('mcp', 'createChat.api'), { remoteId: threadId });
            return { remoteId: threadId, externalId: undefined };
        },

        async rename(remoteId: string, newTitle: string): Promise<void> {
            await ajaxPost(ActionURL.buildURL('mcp', 'renameChat.api'), { remoteId, title: newTitle });
        },

        async archive(remoteId: string): Promise<void> {
            await ajaxPost(ActionURL.buildURL('mcp', 'setArchived.api'), { remoteId, archived: true });
        },

        async unarchive(remoteId: string): Promise<void> {
            await ajaxPost(ActionURL.buildURL('mcp', 'setArchived.api'), { remoteId, archived: false });
        },

        async delete(remoteId: string): Promise<void> {
            await ajaxPost(ActionURL.buildURL('mcp', 'deleteChat.api'), { remoteId });
        },

        async generateTitle(remoteId: string, messages: readonly ThreadMessage[]) {
            // Find the first user text message as the sample
            let sample = '';
            for (const msg of messages) {
                if (msg.role === 'user') {
                    const textPart = msg.content.find((p) => p.type === 'text');
                    if (textPart && 'text' in textPart) {
                        sample = textPart.text;
                        break;
                    }
                }
            }

            let title = 'New chat';
            try {
                const result = await ajaxPost<{ title: string }>(
                    ActionURL.buildURL('mcp', 'generateTitle.api'),
                    { remoteId, sample }
                );
                title = result.title || 'New chat';
            } catch {
                // fall through — return "New chat"
            }

            // Return a proper AssistantStream with the title as a text delta
            return createAssistantStream((controller) => {
                controller.appendText(title);
            });
        },

        unstable_Provider: ThreadProvider,
    };
}
