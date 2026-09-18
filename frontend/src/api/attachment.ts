import request, { get } from './request';
import type { AttachmentVO } from '@/types/inspection';
import type { LongId } from '@/types/auth';

export const attachmentApi = {
  list: (bizType: string, bizId: LongId) =>
    get<AttachmentVO[]>('/attachments', { params: { bizType, bizId } }),
  upload: async (file: File, bizType: string, bizId?: LongId): Promise<AttachmentVO> => {
    const form = new FormData();
    form.append('file', file);
    form.append('bizType', bizType);
    if (bizId !== undefined) form.append('bizId', String(bizId));
    return request.post('/attachments/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    }) as unknown as Promise<AttachmentVO>;
  },
  downloadUrl: (id: LongId) => `/api/v1/attachments/${id}/download`,
};

/** 携带 JWT 下载附件并触发浏览器保存 */
export async function downloadAttachment(id: LongId, fileName?: string) {
  const resp = await request.get(`/attachments/${id}/download`, { responseType: 'blob' });
  const blob = new Blob([resp as unknown as BlobPart]);
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName || 'attachment';
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}
