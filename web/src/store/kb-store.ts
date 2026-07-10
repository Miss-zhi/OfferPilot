/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { create } from 'zustand';

export interface KbState {
  currentKbId: string | null;
  currentKbName: string | null;

  setCurrentKb: (kbId: string, kbName: string) => void;
  clearCurrentKb: () => void;
}

export const useKbStore = create<KbState>()((set) => ({
  currentKbId: null,
  currentKbName: null,

  setCurrentKb: (kbId, kbName) => set({ currentKbId: kbId, currentKbName: kbName }),
  clearCurrentKb: () => set({ currentKbId: null, currentKbName: null }),
}));
