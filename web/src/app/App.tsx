/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, App as AntApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useAuthStore } from '@/store/auth-store';
import { LoginPage } from '@/ui/pages/login/LoginPage';
import { ChatPage } from '@/ui/pages/chat/ChatPage';
import { ReportPage } from '@/ui/pages/reports/ReportPage';
import { ResumePage } from '@/ui/pages/resume/ResumePage';
import { KnowledgeListPage } from '@/ui/pages/admin/knowledge/KnowledgeListPage';
import { DocListPage } from '@/ui/pages/admin/documents/DocListPage';
import { SearchTestPage } from '@/ui/pages/admin/search/SearchTestPage';
import { ProgressPage } from '@/ui/pages/progress/ProgressPage';
import { SalaryPage } from '@/ui/pages/salary/SalaryPage';
import { ModelConfigPage } from '@/ui/pages/admin/models/ModelConfigPage';
import { SettingsPage } from '@/ui/pages/settings/SettingsPage';

function AuthGuard({ children }: { children: ReactNode }) {
  const isAuth = useAuthStore((s) => s.isAuthenticated());
  if (!isAuth) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

export function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 6,
        },
      }}
    >
      <AntApp>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/chat"
              element={
                <AuthGuard>
                  <ChatPage />
                </AuthGuard>
              }
            />
            <Route
              path="/reports/:id"
              element={
                <AuthGuard>
                  <ReportPage />
                </AuthGuard>
              }
            />
            <Route
              path="/resume"
              element={
                <AuthGuard>
                  <ResumePage />
                </AuthGuard>
              }
            />
            <Route
              path="/admin/knowledge"
              element={
                <AuthGuard>
                  <KnowledgeListPage />
                </AuthGuard>
              }
            />
            <Route
              path="/admin/knowledge/:kbId"
              element={
                <AuthGuard>
                  <DocListPage />
                </AuthGuard>
              }
            />
            <Route
              path="/admin/knowledge/:kbId/search"
              element={
                <AuthGuard>
                  <SearchTestPage />
                </AuthGuard>
              }
            />
            <Route
              path="/progress"
              element={
                <AuthGuard>
                  <ProgressPage />
                </AuthGuard>
              }
            />
            <Route
              path="/salary"
              element={
                <AuthGuard>
                  <SalaryPage />
                </AuthGuard>
              }
            />
            <Route
              path="/admin/models"
              element={
                <AuthGuard>
                  <ModelConfigPage />
                </AuthGuard>
              }
            />
            <Route
              path="/settings"
              element={
                <AuthGuard>
                  <SettingsPage />
                </AuthGuard>
              }
            />
            <Route path="*" element={<Navigate to="/chat" replace />} />
          </Routes>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}
