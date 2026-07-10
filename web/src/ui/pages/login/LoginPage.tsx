/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, Tabs, message } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons';
import { authService } from '@/service/authService';
import type { LoginRequest, RegisterRequest } from '@/service/authService';
import { useAuthStore } from '@/store/auth-store';

export function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [activeTab, setActiveTab] = useState<'login' | 'register'>('login');
  const [loading, setLoading] = useState(false);
  const [loginForm] = Form.useForm();
  const [registerForm] = Form.useForm();

  const handleLogin = async (values: LoginRequest) => {
    setLoading(true);
    try {
      const res = await authService.login(values);
      setAuth(res.token, res.userId, res.username, res.role);
      message.success('登录成功');
      navigate('/chat', { replace: true });
    } catch (e) {
      message.error(e instanceof Error ? e.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: RegisterRequest) => {
    setLoading(true);
    try {
      const res = await authService.register(values);
      setAuth(res.token, res.userId, res.username, res.role);
      message.success('注册成功，已自动登录');
      navigate('/chat', { replace: true });
    } catch (e) {
      message.error(e instanceof Error ? e.message : '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      }}
    >
      <Card
        style={{ width: 420, boxShadow: '0 8px 24px rgba(0,0,0,0.15)' }}
        title={
          <div style={{ textAlign: 'center', fontSize: 20, fontWeight: 600 }}>
            OfferPilot
          </div>
        }
      >
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key as 'login' | 'register')}
          centered
          items={[
            {
              key: 'login',
              label: '登录',
              children: (
                <Form form={loginForm} onFinish={handleLogin} size="large">
                  <Form.Item
                    name="username"
                    rules={[
                      { required: true, message: '请输入用户名' },
                      { min: 2, max: 128, message: '用户名长度 2-128' },
                    ]}
                  >
                    <Input prefix={<UserOutlined />} placeholder="用户名" />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 6, max: 128, message: '密码长度 6-128' },
                    ]}
                  >
                    <Input.Password prefix={<LockOutlined />} placeholder="密码" />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" htmlType="submit" block loading={loading}>
                      登录
                    </Button>
                  </Form.Item>
                </Form>
              ),
            },
            {
              key: 'register',
              label: '注册',
              children: (
                <Form form={registerForm} onFinish={handleRegister} size="large">
                  <Form.Item
                    name="username"
                    rules={[
                      { required: true, message: '请输入用户名' },
                      { min: 2, max: 128, message: '用户名长度 2-128' },
                    ]}
                  >
                    <Input prefix={<UserOutlined />} placeholder="用户名" />
                  </Form.Item>
                  <Form.Item
                    name="email"
                    rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
                  >
                    <Input prefix={<MailOutlined />} placeholder="邮箱（选填）" />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 6, max: 128, message: '密码长度 6-128' },
                    ]}
                  >
                    <Input.Password prefix={<LockOutlined />} placeholder="密码" />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" htmlType="submit" block loading={loading}>
                      注册
                    </Button>
                  </Form.Item>
                </Form>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
