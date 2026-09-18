import React from 'react';
import { Button, Space, Spin, Tag, Tooltip, Upload } from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { attachmentApi, downloadAttachment } from '@/api/attachment';
import { useAuthStore } from '@/store/authStore';
import type { AttachmentVO } from '@/types/inspection';
import type { LongId } from '@/types/auth';
import message from '@/utils/feedback';

interface Props {
  bizType: string;
  bizId?: LongId;
  /** 受控：当前选中附件（检验结果项） */
  value?: LongId;
  onChange?: (id: LongId | undefined, attachment?: AttachmentVO) => void;
  accept?: string;
  multiple?: boolean;
  readOnly?: boolean;
}

/**
 * 附件上传/查看：服务端校验图片/PDF与大小，元数据只增。
 */
const AttachmentUpload: React.FC<Props> = ({
  bizType,
  bizId,
  value,
  onChange,
  accept = 'image/*,.pdf',
  readOnly,
}) => {
  const queryClient = useQueryClient();
  const token = useAuthStore((s) => s.accessToken);
  const enabled = bizId !== undefined && bizId !== null;
  const listQuery = useQuery({
    queryKey: ['attachments', bizType, bizId],
    queryFn: () => attachmentApi.list(bizType, bizId as LongId),
    enabled,
  });

  const attachments = listQuery.data || [];
  const selected = value ? attachments.find((a) => String(a.id) === String(value)) : undefined;

  return (
    <Space direction="vertical" size={4} style={{ width: '100%' }}>
      {listQuery.isFetching && <Spin size="small" />}
      {attachments.length > 0 && (
        <Space wrap size={4}>
          {attachments.map((a) => {
            const active = value ? String(a.id) === String(value) : true;
            return (
              <Tooltip title={a.contentType} key={String(a.id)}>
                <Tag
                  color={active ? 'blue' : 'default'}
                  style={{ cursor: 'pointer', marginInlineEnd: 0 }}
                  onClick={() => onChange?.(active ? undefined : a.id, active ? undefined : a)}
                >
                  {a.fileName}
                </Tag>
              </Tooltip>
            );
          })}
        </Space>
      )}
      {selected && (
        <Button
          type="link"
          size="small"
          style={{ padding: 0 }}
          icon={<DownloadOutlined />}
          onClick={() => downloadAttachment(selected.id, selected.fileName)}
        >
          下载 {selected.fileName}
        </Button>
      )}
      {!readOnly && (
        <Upload
          accept={accept}
          showUploadList={false}
          headers={{ Authorization: `Bearer ${token}` }}
          action={`/api/v1/attachments/upload`}
          data={{ bizType, bizId }}
          withCredentials={false}
          onChange={(info) => {
            if (info.file.status === 'done') {
              const body = info.file.response;
              if (body?.code === '0') {
                message.success('附件上传成功');
                onChange?.(body.data.id, body.data);
                if (enabled) {
                  void queryClient.invalidateQueries({ queryKey: ['attachments', bizType, bizId] });
                }
              } else {
                message.error(body?.message || '上传失败');
              }
            } else if (info.file.status === 'error') {
              message.error('上传失败');
            }
          }}
        >
          <Button size="small" icon={<UploadOutlined />}>上传凭证</Button>
        </Upload>
      )}
    </Space>
  );
};

export default AttachmentUpload;
