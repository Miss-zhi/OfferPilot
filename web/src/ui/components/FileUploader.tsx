/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { useState } from 'react';
import { Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { UPLOAD_LIMITS } from '@/infra/constants';

const { Dragger } = Upload;

interface FileUploaderProps {
  accept?: string;
  maxSize?: number;
  onUploaded: (filePath: string, fileName: string) => void;
  uploadFn: (file: File) => Promise<{ filePath: string }>;
  disabled?: boolean;
}

export function FileUploader({
  accept = UPLOAD_LIMITS.ACCEPT,
  maxSize = UPLOAD_LIMITS.MAX_SIZE,
  onUploaded,
  uploadFn,
  disabled = false,
}: FileUploaderProps) {
  const [uploading, setUploading] = useState(false);

  const props: UploadProps = {
    name: 'file',
    multiple: false,
    accept,
    showUploadList: false,
    disabled: disabled || uploading,
    beforeUpload: (file) => {
      if (file.size > maxSize) {
        message.error(`文件大小不能超过 ${maxSize / 1024 / 1024}MB`);
        return Upload.LIST_IGNORE;
      }
      return true;
    },
    customRequest: async (options) => {
      const { file, onSuccess, onError } = options;
      setUploading(true);
      try {
        const result = await uploadFn(file as File);
        onSuccess?.(result);
        message.success(`${(file as File).name} 上传成功`);
        onUploaded(result.filePath, (file as File).name);
      } catch (e) {
        onError?.(e as Error);
        message.error(`${(file as File).name} 上传失败`);
      } finally {
        setUploading(false);
      }
    },
  };

  return (
    <Dragger {...props}>
      <p className="ant-upload-drag-icon">
        <InboxOutlined />
      </p>
      <p className="ant-upload-text">
        {uploading ? '正在上传...' : '点击或拖拽文件到此区域上传'}
      </p>
      <p className="ant-upload-hint">
        支持 PDF、TXT、MD、DOCX、MP3、WAV、M4A 格式，最大 {maxSize / 1024 / 1024}MB
      </p>
    </Dragger>
  );
}
