import { useLocalRuntime, type ChatModelAdapter } from '@assistant-ui/react';
import { ActionURL, Ajax } from '@labkey/api';

interface ChatApiResponse {
    contentType: string;
    response: string;
    success: boolean;
    error?: string;
}

const labkeyAdapter: ChatModelAdapter = {
    async *run({ messages, abortSignal }) {
        const lastMessage = messages[messages.length - 1];
        if (!lastMessage || lastMessage.role !== 'user') {
            return;
        }

        const textParts = lastMessage.content.filter(
            (part): part is { type: 'text'; text: string } => part.type === 'text'
        );
        const prompt = textParts.map((p) => p.text).join('\n');

        if (!prompt.trim()) {
            return;
        }

        // Yield an empty text part immediately so the assistant bubble is rendered
        // and the Empty message-part component (thinking indicator) shows up while
        // we wait for the server. Without this, the viewport shows nothing until
        // the request returns.
        yield {
            content: [{ type: 'text' as const, text: '' }],
        };

        const url = ActionURL.buildURL('mcp', 'chatAgent.api');

        const response = await new Promise<ChatApiResponse>((resolve, reject) => {
            const xhr = Ajax.request({
                url,
                method: 'POST',
                jsonData: { prompt },
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
                    try {
                        xhr.abort();
                    } catch {
                        // ignore
                    }
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
                content: [
                    {
                        type: 'text' as const,
                        text: response.error || response.response || 'An error occurred.',
                    },
                ],
            };
            return;
        }

        yield {
            content: [{ type: 'text' as const, text: response.response || 'No response.' }],
        };
    },
};

export function useLabKeyRuntime() {
    return useLocalRuntime(labkeyAdapter);
}
