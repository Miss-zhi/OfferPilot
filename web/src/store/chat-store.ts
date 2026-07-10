/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { create } from 'zustand';

export interface ChatMessage {
  id: string;
  role: 'user' | 'ai';
  content: string;
  timestamp: number;
}

export interface ChatState {
  sessionId: string | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  activeFunction: string;

  setSessionId: (id: string) => void;
  addMessage: (msg: ChatMessage) => void;
  appendStreamContent: (msgId: string, chunk: string) => void;
  setStreaming: (streaming: boolean) => void;
  setActiveFunction: (fn: string) => void;
  resetSession: () => void;
  clearMessages: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  sessionId: null,
  messages: [],
  isStreaming: false,
  activeFunction: 'mock_interview',

  setSessionId: (id) => set({ sessionId: id }),

  addMessage: (msg) =>
    set((state) => ({ messages: [...state.messages, msg] })),

  appendStreamContent: (msgId, chunk) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === msgId ? { ...m, content: m.content + chunk } : m,
      ),
    })),

  setStreaming: (streaming) => set({ isStreaming: streaming }),

  setActiveFunction: (fn) => set({ activeFunction: fn }),

  resetSession: () =>
    set({ sessionId: null, messages: [], isStreaming: false }),

  clearMessages: () => set({ messages: [] }),
}));
