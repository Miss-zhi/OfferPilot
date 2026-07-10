/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { MarkdownRenderer } from './MarkdownRenderer';

interface SseStreamRendererProps {
  content: string;
}

export function SseStreamRenderer({ content }: SseStreamRendererProps) {
  return <MarkdownRenderer content={content} />;
}
