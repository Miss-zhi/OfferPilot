/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';

export interface ResumeUploadResult {
  filePath: string;
  fileType: string;
  size: number;
}

export const resumeService = {
  /** 上传简历 PDF */
  uploadResume: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', 'resume');
    return http
      .post<ResumeUploadResult>('/v1/offerpilot/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((res) => res.data);
  },
};
