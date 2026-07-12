/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState, useRef, useCallback } from 'react';
import { Input, Button, List, Modal, Dropdown, message } from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useChatStore } from '@/store/chat-store';
import { chatService, CHAT_FUNCTIONS } from '@/service/chatService';
import type { SearchResultItem } from '@/service/chatService';

const functionLabels: Record<string, string> = {};
CHAT_FUNCTIONS.forEach((f) => {
  functionLabels[f.key] = f.label;
});

/** 会话列表侧边栏组件 */
export function SessionList() {
  const {
    sessions,
    currentSessionId,
    isStreaming,
    switchSession,
    createNewSession,
    deleteSession,
    renameSession,
  } = useChatStore();

  const [searchValue, setSearchValue] = useState('');
  const [searchResults, setSearchResults] = useState<SearchResultItem[] | null>(null);
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState<{ sessionId: string; title: string } | null>(null);
  const [renameInput, setRenameInput] = useState('');
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  /** 搜索防抖 */
  const handleSearchChange = useCallback((value: string) => {
    setSearchValue(value);
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    if (!value.trim()) {
      setSearchResults(null);
      return;
    }
    searchTimerRef.current = setTimeout(async () => {
      try {
        const results = await chatService.searchSessions(value.trim());
        setSearchResults(results);
      } catch {
        // 搜索失败静默处理
      }
    }, 400);
  }, []);

  /** 新建会话 */
  const handleCreate = useCallback(async () => {
    if (isStreaming) {
      message.warning('请等待当前对话完成后再创建新会话');
      return;
    }
    try {
      await createNewSession();
    } catch {
      message.error('创建会话失败');
    }
  }, [isStreaming, createNewSession]);

  /** 打开重命名弹窗 */
  const openRename = useCallback((sessionId: string, title: string) => {
    setRenameTarget({ sessionId, title });
    setRenameInput(title);
    setRenameModalOpen(true);
  }, []);

  /** 确认重命名 */
  const handleRenameConfirm = useCallback(async () => {
    if (!renameTarget) return;
    try {
      await renameSession(renameTarget.sessionId, renameInput.trim() || '未命名对话');
      setRenameModalOpen(false);
      setRenameTarget(null);
    } catch {
      message.error('重命名失败');
    }
  }, [renameTarget, renameInput, renameSession]);

  /** 删除会话 */
  const handleDelete = useCallback(
    async (sessionId: string) => {
      Modal.confirm({
        title: '删除会话',
        content: '删除后将无法恢复，确定删除？',
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: async () => {
          try {
            await deleteSession(sessionId);
          } catch {
            message.error('删除失败');
          }
        },
      });
    },
    [deleteSession],
  );

  /** 右键菜单 */
  const contextMenuItems = useCallback(
    (sessionId: string, title: string): MenuProps['items'] => [
      {
        key: 'rename',
        icon: <EditOutlined />,
        label: '重命名',
        onClick: () => openRename(sessionId, title),
      },
      { type: 'divider' },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除',
        danger: true,
        onClick: () => handleDelete(sessionId),
      },
    ],
    [openRename, handleDelete],
  );

  // 展示的列表：搜索结果 or 正常列表
  const displayList = searchResults
    ? searchResults.map((r) => ({
        sessionId: r.sessionId,
        title: r.sessionTitle || '未命名对话',
        activeFunction: '',
        messageCount: r.matchCount,
        isSearchResult: true,
        matchSnippet: r.matchSnippet,
      }))
    : sessions.map((s) => ({
        sessionId: s.sessionId,
        title: s.title || '未命名对话',
        activeFunction: s.activeFunction,
        messageCount: s.messageCount,
        isSearchResult: false,
        matchSnippet: undefined as string | undefined,
      }));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* 搜索 + 新建 */}
      <div style={{ padding: '8px 12px', borderBottom: '1px solid #f0f0f0' }}>
        <Input
          size="small"
          placeholder="搜索对话..."
          prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
          value={searchValue}
          onChange={(e) => handleSearchChange(e.target.value)}
          allowClear
          style={{ marginBottom: 8 }}
        />
        <Button
          type="dashed"
          block
          icon={<PlusOutlined />}
          onClick={handleCreate}
          disabled={isStreaming}
        >
          新建对话
        </Button>
      </div>

      {/* 会话列表 */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <List
          dataSource={displayList}
          renderItem={(item) => (
            <Dropdown
              menu={{ items: contextMenuItems(item.sessionId, item.title) }}
              trigger={['contextMenu']}
              key={item.sessionId}
            >
              <div
                onDoubleClick={() => {
                  if (!item.isSearchResult) {
                    openRename(item.sessionId, item.title);
                  }
                }}
                onClick={() => {
                  if (isStreaming) return;
                  if (item.isSearchResult) {
                    // 搜索结果 → 切换到该会话
                    switchSession(item.sessionId);
                    setSearchValue('');
                    setSearchResults(null);
                  } else {
                    switchSession(item.sessionId);
                  }
                }}
                style={{
                  padding: '10px 12px',
                  cursor: 'pointer',
                  background:
                    currentSessionId === item.sessionId ? '#e6f4ff' : 'transparent',
                  borderBottom: '1px solid #fafafa',
                  transition: 'background 0.2s',
                }}
                onMouseEnter={(e) => {
                  if (currentSessionId !== item.sessionId) {
                    (e.currentTarget as HTMLDivElement).style.background = '#f5f5f5';
                  }
                }}
                onMouseLeave={(e) => {
                  if (currentSessionId !== item.sessionId) {
                    (e.currentTarget as HTMLDivElement).style.background = 'transparent';
                  }
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                  }}
                >
                  <MessageOutlined style={{ color: '#8c8c8c', fontSize: 14 }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        fontWeight: 500,
                        fontSize: 13,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {item.title}
                    </div>
                    {item.matchSnippet && (
                      <div
                        style={{
                          fontSize: 11,
                          color: '#999',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          marginTop: 2,
                        }}
                      >
                        {item.matchSnippet}
                      </div>
                    )}
                    <div style={{ fontSize: 11, color: '#bbb', marginTop: 2 }}>
                      {item.isSearchResult
                        ? `${item.messageCount} 条匹配`
                        : `${functionLabels[item.activeFunction] || item.activeFunction} · ${item.messageCount} 条消息`}
                    </div>
                  </div>
                </div>
              </div>
            </Dropdown>
          )}
          locale={{ emptyText: searchResults ? '未找到匹配的对话' : '暂无对话记录' }}
        />
      </div>

      {/* 重命名弹窗 */}
      <Modal
        title="重命名会话"
        open={renameModalOpen}
        onOk={handleRenameConfirm}
        onCancel={() => {
          setRenameModalOpen(false);
          setRenameTarget(null);
        }}
        okText="确定"
        cancelText="取消"
        destroyOnClose
      >
        <Input
          value={renameInput}
          onChange={(e) => setRenameInput(e.target.value)}
          onPressEnter={handleRenameConfirm}
          maxLength={200}
          placeholder="输入新标题"
        />
      </Modal>
    </div>
  );
}
