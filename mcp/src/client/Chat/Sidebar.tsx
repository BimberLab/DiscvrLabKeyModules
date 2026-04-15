import React, { type FC, useState } from 'react';
import {
    ThreadListPrimitive,
    ThreadListItemPrimitive,
} from '@assistant-ui/react';

// --- Icons ------------------------------------------------------------------

const NewChatIcon: FC = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"
         strokeLinejoin="round" aria-hidden="true">
        <path d="M12 5v14" />
        <path d="M5 12h14" />
    </svg>
);

const ArchiveIcon: FC = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"
         strokeLinejoin="round" aria-hidden="true">
        <rect x="2" y="3" width="20" height="5" rx="1" />
        <path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8" />
        <path d="M10 12h4" />
    </svg>
);

const DeleteIcon: FC = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"
         strokeLinejoin="round" aria-hidden="true">
        <polyline points="3 6 5 6 21 6" />
        <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
        <path d="M10 11v6" />
        <path d="M14 11v6" />
        <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
    </svg>
);

const UnarchiveIcon: FC = () => (
    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"
         strokeLinejoin="round" aria-hidden="true">
        <rect x="2" y="3" width="20" height="5" rx="1" />
        <path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8" />
        <path d="M12 12v6" />
        <path d="M9 15l3-3 3 3" />
    </svg>
);

const ChevronIcon: FC<{ open: boolean }> = ({ open }) => (
    <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"
         strokeLinejoin="round" aria-hidden="true"
         style={{ transform: open ? 'rotate(90deg)' : 'none', transition: 'transform 0.15s ease' }}>
        <path d="M9 18l6-6-6-6" />
    </svg>
);

// --- Thread list item -------------------------------------------------------

const ThreadListItem: FC = () => (
    <ThreadListItemPrimitive.Root className="mcp-thread-list-item">
        <ThreadListItemPrimitive.Trigger className="mcp-thread-list-trigger">
            <span className="mcp-thread-list-title">
                <ThreadListItemPrimitive.Title fallback="New chat" />
            </span>
        </ThreadListItemPrimitive.Trigger>
        <div className="mcp-thread-list-actions">
            <ThreadListItemPrimitive.Archive
                className="mcp-thread-list-action-btn"
                aria-label="Archive"
            >
                <ArchiveIcon />
            </ThreadListItemPrimitive.Archive>
            <ThreadListItemPrimitive.Delete
                className="mcp-thread-list-action-btn mcp-thread-list-action-btn-danger"
                aria-label="Delete"
            >
                <DeleteIcon />
            </ThreadListItemPrimitive.Delete>
        </div>
    </ThreadListItemPrimitive.Root>
);

const ArchivedThreadListItem: FC = () => (
    <ThreadListItemPrimitive.Root className="mcp-thread-list-item">
        <ThreadListItemPrimitive.Trigger className="mcp-thread-list-trigger">
            <span className="mcp-thread-list-title">
                <ThreadListItemPrimitive.Title fallback="New chat" />
            </span>
        </ThreadListItemPrimitive.Trigger>
        <div className="mcp-thread-list-actions">
            <ThreadListItemPrimitive.Unarchive
                className="mcp-thread-list-action-btn"
                aria-label="Unarchive"
            >
                <UnarchiveIcon />
            </ThreadListItemPrimitive.Unarchive>
            <ThreadListItemPrimitive.Delete
                className="mcp-thread-list-action-btn mcp-thread-list-action-btn-danger"
                aria-label="Delete"
            >
                <DeleteIcon />
            </ThreadListItemPrimitive.Delete>
        </div>
    </ThreadListItemPrimitive.Root>
);

// --- Sidebar ----------------------------------------------------------------

export const Sidebar: FC = () => {
    const [archivedOpen, setArchivedOpen] = useState(false);

    return (
        <aside className="mcp-sidebar">
            <div className="mcp-sidebar-header">
                <span className="mcp-sidebar-label">Chats</span>
                <ThreadListPrimitive.New
                    className="mcp-new-chat-btn"
                    aria-label="New chat"
                >
                    <NewChatIcon />
                    <span>New chat</span>
                </ThreadListPrimitive.New>
            </div>

            <div className="mcp-sidebar-list">
                <ThreadListPrimitive.Root>
                    <ThreadListPrimitive.Items
                        components={{ ThreadListItem }}
                    />
                </ThreadListPrimitive.Root>
            </div>

            <div className="mcp-sidebar-archived">
                <button
                    type="button"
                    className="mcp-archived-toggle"
                    onClick={() => setArchivedOpen((o) => !o)}
                >
                    <ChevronIcon open={archivedOpen} />
                    <span>Archived</span>
                </button>
                {archivedOpen && (
                    <ThreadListPrimitive.Root>
                        <ThreadListPrimitive.Items
                            archived
                            components={{ ThreadListItem: ArchivedThreadListItem }}
                        />
                    </ThreadListPrimitive.Root>
                )}
            </div>
        </aside>
    );
};
