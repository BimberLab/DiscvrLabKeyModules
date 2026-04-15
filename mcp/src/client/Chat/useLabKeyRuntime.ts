import { useMemo } from 'react';
import { useLocalThreadRuntime, useThreadListItemRuntime, type ChatModelAdapter } from '@assistant-ui/react';
import { ActionURL, Ajax } from '@labkey/api';

interface HistoryEntry {
    role: string;
    text: string;
}

interface ChatApiResponse {
    contentType: string;
    response: string;
    success: boolean;
    error?: string;
}

function textPartsOf(content: readonly { type: string; text?: string }[]): string {
    return content
        .filter((p): p is { type: 'text'; text: string } => p.type === 'text')
        .map((p) => p.text)
        .join('\n');
}

export function useLabKeyRuntime() {
    // threadListItemRuntime is a stable zustand-backed object — safe to capture in a memo
    const threadListItemRuntime = useThreadListItemRuntime({ optional: true });

    // Stable adapter: reads threadListItemRuntime inside run() at call time
    const adapter = useMemo((): ChatModelAdapter => ({
        async *run({ messages, abortSignal }) {
            const lastMessage = messages[messages.length - 1];
            if (!lastMessage || lastMessage.role !== 'user') return;

            const prompt = textPartsOf(lastMessage.content as { type: string; text?: string }[]);
            if (!prompt.trim()) return;

            // Yield empty text immediately so the thinking indicator renders
            yield { content: [{ type: 'text' as const, text: '' }] };

            // Build history from all prior messages (all except the last user message)
            const history: HistoryEntry[] = messages
                .slice(0, -1)
                .filter((m) => m.role === 'user' || m.role === 'assistant')
                .map((m) => ({
                    role: m.role,
                    text: textPartsOf(m.content as { type: string; text?: string }[]),
                }))
                .filter((e) => e.text.trim().length > 0);

            // Get the current thread's remoteId (may be undefined for brand-new threads)
            const remoteId = threadListItemRuntime?.getState().remoteId ?? undefined;

            const url = ActionURL.buildURL('mcp', 'chatAgent.api');

            const response = await new Promise<ChatApiResponse>((resolve, reject) => {
                const xhr = Ajax.request({
                    url,
                    method: 'POST',
                    jsonData: { prompt, history, remoteId },
                    success: (x: XMLHttpRequest) => {
                        try {
                            resolve(JSON.parse(x.responseText));
                        } catch {
                            reject(new Error('Failed to parse response'));
                        }
                    },
                    failure: (x: XMLHttpRequest) => {
                        reject(new Error(x.statusText || 'Request failed'));
                    },
                });

                if (abortSignal) {
                    const onAbort = () => {
                        try { xhr.abort(); } catch { /* ignore */ }
                        reject(new DOMException('Aborted', 'AbortError'));
                    };
                    if (abortSignal.aborted) {
                        onAbort();
                    } else {
                        abortSignal.addEventListener('abort', onAbort, { once: true });
                    }
                }
            });

            if (!response.success) {
                yield {
                    content: [{
                        type: 'text' as const,
                        text: response.error || response.response || 'An error occurred.',
                    }],
                };
                return;
            }

            yield {
                content: [{ type: 'text' as const, text: response.response || 'No response.' }],
            };
        },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }), []); // threadListItemRuntime is stable — no deps needed

    return useLocalThreadRuntime(adapter, {});
}
