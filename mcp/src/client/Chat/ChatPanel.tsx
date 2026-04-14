import React, { type FC } from 'react';
import {
    AssistantRuntimeProvider,
    ThreadPrimitive,
    ComposerPrimitive,
    MessagePrimitive,
    ActionBarPrimitive,
} from '@assistant-ui/react';
import { MarkdownText } from './MarkdownText';
import { useLabKeyRuntime } from './useLabKeyRuntime';
import './ChatPanel.css';

const STARTER_PROMPTS = [
    {
        title: 'Explore schemas',
        body: 'List the available schemas in this folder',
    },
    {
        title: 'Inspect a table',
        body: 'Show the columns in the core.Users table',
    },
    {
        title: 'Orient me',
        body: 'Describe this LabKey server and what I can do here',
    },
    {
        title: 'Find reports',
        body: 'What queries or reports exist in the current folder?',
    },
];

// --- Icons --------------------------------------------------------------

const Icon: FC<React.SVGProps<SVGSVGElement>> = (props) => (
    <svg
        xmlns="http://www.w3.org/2000/svg"
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        {...props}
    />
);

const SendIcon: FC = () => (
    <Icon>
        <path d="M22 2 11 13" />
        <path d="M22 2 15 22 11 13 2 9 22 2z" />
    </Icon>
);

const StopIcon: FC = () => (
    <Icon>
        <rect x="6" y="6" width="12" height="12" rx="1.5" />
    </Icon>
);

const CopyIcon: FC = () => (
    <Icon>
        <rect x="9" y="9" width="13" height="13" rx="2" />
        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </Icon>
);

const ReloadIcon: FC = () => (
    <Icon>
        <path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
        <path d="M21 3v5h-5" />
        <path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
        <path d="M3 21v-5h5" />
    </Icon>
);

const ArrowDownIcon: FC = () => (
    <Icon>
        <path d="M12 5v14" />
        <path d="m19 12-7 7-7-7" />
    </Icon>
);

const SparkleIcon: FC<{ size?: number }> = ({ size = 28 }) => (
    <Icon width={size} height={size} strokeWidth="1.6">
        <path d="M12 3v4" />
        <path d="M12 17v4" />
        <path d="M3 12h4" />
        <path d="M17 12h4" />
        <path d="M5.6 5.6l2.8 2.8" />
        <path d="M15.6 15.6l2.8 2.8" />
        <path d="M5.6 18.4l2.8-2.8" />
        <path d="M15.6 8.4l2.8-2.8" />
    </Icon>
);

// --- Structural components ----------------------------------------------

const EmptyState: FC = () => (
    <ThreadPrimitive.Empty>
        <div className="mcp-empty">
            <div className="mcp-empty-badge">
                <SparkleIcon size={32} />
            </div>
            <h2 className="mcp-empty-title">How can I help with your LabKey data?</h2>
            <p className="mcp-empty-subtitle">
                Ask about schemas, tables, queries, studies, or search this server.
                I can use LabKey's MCP tools to explore your folders and answer data
                questions.
            </p>
            <div className="mcp-suggestions">
                {STARTER_PROMPTS.map((prompt) => (
                    <ThreadPrimitive.Suggestion
                        key={prompt.title}
                        className="mcp-suggestion"
                        prompt={prompt.body}
                        method="replace"
                        autoSend
                    >
                        <span className="mcp-suggestion-title">{prompt.title}</span>
                        <span className="mcp-suggestion-body">{prompt.body}</span>
                    </ThreadPrimitive.Suggestion>
                ))}
            </div>
        </div>
    </ThreadPrimitive.Empty>
);

const ThinkingIndicator: FC = () => (
    <div className="mcp-thinking" aria-label="Assistant is thinking">
        <span />
        <span />
        <span />
    </div>
);

const AssistantAvatar: FC = () => (
    <div className="mcp-avatar mcp-avatar-assistant" aria-hidden="true">
        <SparkleIcon size={16} />
    </div>
);

const UserAvatar: FC = () => (
    <div className="mcp-avatar mcp-avatar-user" aria-hidden="true">
        <Icon width="16" height="16">
            <circle cx="12" cy="8" r="4" />
            <path d="M4 21a8 8 0 0 1 16 0" />
        </Icon>
    </div>
);

const UserMessage: FC = () => (
    <MessagePrimitive.Root className="mcp-message mcp-message-user">
        <div className="mcp-user-bubble">
            <MessagePrimitive.Content />
        </div>
        <UserAvatar />
    </MessagePrimitive.Root>
);

const AssistantMessage: FC = () => (
    <MessagePrimitive.Root className="mcp-message mcp-message-assistant">
        <AssistantAvatar />
        <div className="mcp-assistant-column">
            <div className="mcp-assistant-body">
                <MessagePrimitive.Content
                    components={{
                        Text: MarkdownText,
                        Empty: ThinkingIndicator,
                    }}
                />
            </div>
            <ActionBarPrimitive.Root
                className="mcp-assistant-actions"
                hideWhenRunning
                autohide="not-last"
                autohideFloat="single-branch"
            >
                <ActionBarPrimitive.Copy className="mcp-action-btn" aria-label="Copy message">
                    <CopyIcon />
                    <span>Copy</span>
                </ActionBarPrimitive.Copy>
                <ActionBarPrimitive.Reload className="mcp-action-btn" aria-label="Regenerate response">
                    <ReloadIcon />
                    <span>Regenerate</span>
                </ActionBarPrimitive.Reload>
            </ActionBarPrimitive.Root>
        </div>
    </MessagePrimitive.Root>
);

const Composer: FC = () => (
    <div className="mcp-composer-wrap">
        <ComposerPrimitive.Root className="mcp-composer">
            <ComposerPrimitive.Input
                className="mcp-composer-input"
                placeholder="Ask about your LabKey data..."
                rows={1}
                maxRows={8}
                autoFocus
            />
            <ThreadPrimitive.If running={false}>
                <ComposerPrimitive.Send className="mcp-icon-btn mcp-send-btn" aria-label="Send message">
                    <SendIcon />
                </ComposerPrimitive.Send>
            </ThreadPrimitive.If>
            <ThreadPrimitive.If running>
                <ComposerPrimitive.Cancel className="mcp-icon-btn mcp-cancel-btn" aria-label="Stop generating">
                    <StopIcon />
                </ComposerPrimitive.Cancel>
            </ThreadPrimitive.If>
        </ComposerPrimitive.Root>
        <p className="mcp-composer-hint">
            Press <kbd>Enter</kbd> to send, <kbd>Shift</kbd>+<kbd>Enter</kbd> for a new line.
        </p>
    </div>
);

const Thread: FC = () => (
    <ThreadPrimitive.Root className="mcp-thread-root">
        <ThreadPrimitive.Viewport className="mcp-viewport">
            <div className="mcp-thread-inner">
                <EmptyState />
                <ThreadPrimitive.Messages
                    components={{
                        UserMessage,
                        AssistantMessage,
                    }}
                />
            </div>
        </ThreadPrimitive.Viewport>
        <ThreadPrimitive.ScrollToBottom
            className="mcp-scroll-to-bottom"
            aria-label="Scroll to bottom"
        >
            <ArrowDownIcon />
        </ThreadPrimitive.ScrollToBottom>
        <Composer />
    </ThreadPrimitive.Root>
);

export const ChatPanel: FC = () => {
    const runtime = useLabKeyRuntime();

    return (
        <AssistantRuntimeProvider runtime={runtime}>
            <div className="mcp-chat">
                <Thread />
            </div>
        </AssistantRuntimeProvider>
    );
};
