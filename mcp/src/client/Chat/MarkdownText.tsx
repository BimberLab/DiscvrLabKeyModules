import React, { useState, useCallback, type FC } from 'react';
import {
    MarkdownTextPrimitive,
    unstable_memoizeMarkdownComponents as memoizeMarkdownComponents,
    type CodeHeaderProps,
    type SyntaxHighlighterProps,
} from '@assistant-ui/react-markdown';

const CodeHeader: FC<CodeHeaderProps> = ({ language, code }) => {
    const [copied, setCopied] = useState(false);

    const onCopy = useCallback(() => {
        if (!code) return;
        void navigator.clipboard.writeText(code).then(() => {
            setCopied(true);
            window.setTimeout(() => setCopied(false), 1500);
        });
    }, [code]);

    return (
        <div className="mcp-code-header">
            <span className="mcp-code-language">{language || 'text'}</span>
            <button type="button" className="mcp-code-copy" onClick={onCopy} aria-label="Copy code">
                {copied ? 'Copied' : 'Copy'}
            </button>
        </div>
    );
};

const PlainSyntaxHighlighter: FC<SyntaxHighlighterProps> = ({ components: { Pre, Code }, code }) => {
    return (
        <Pre>
            <Code>{code}</Code>
        </Pre>
    );
};

type AProps = React.AnchorHTMLAttributes<HTMLAnchorElement> & { node?: unknown };
type TableProps = React.TableHTMLAttributes<HTMLTableElement> & { node?: unknown };

const components = memoizeMarkdownComponents({
    CodeHeader,
    SyntaxHighlighter: PlainSyntaxHighlighter,
    a: ({ node: _n, ...props }: AProps) => (
        <a {...props} target="_blank" rel="noreferrer noopener" />
    ),
    table: ({ node: _n, ...props }: TableProps) => (
        <div className="mcp-table-scroll">
            <table {...props} />
        </div>
    ),
});

export const MarkdownText: FC = () => (
    <MarkdownTextPrimitive smooth className="mcp-markdown" components={components} />
);
