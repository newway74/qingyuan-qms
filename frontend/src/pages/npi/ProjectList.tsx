import React, { useState } from 'react';
import { Button, Card, DatePicker, Form, Input, Modal, Select, Space, Table, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import AuthButton from '@/components/AuthButton';
import CategoryTreeSelect from '@/components/CategoryTreeSelect';
import { npiApi } from '@/api/npi';
import type { ProjectQuery } from '@/api/npi';
import { NPI_STATUS, STD_GRADE_FULL } from '@/constants/qms';
import type { ProjectListRow, ProjectUpsert } from '@/types/npi';
import message from '@/utils/feedback';

const npiMeta = (v: string) => NPI_STATUS.find((s) => s.value === v);

const ProjectList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<ProjectQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['npi-projects', query], queryFn: () => npiApi.page(query) });

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectListRow | null>(null);
  const [form] = Form.useForm<
    Omit<ProjectUpsert, 'meetingAt' | 'targetListingDate'> & {
      meetingAt?: dayjs.Dayjs;
      targetListingDate?: dayjs.Dayjs;
    }
  >();

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['npi-projects'] });

  const saveMutation = useMutation({
    mutationFn: async (v: ProjectUpsert) => {
      if (editing) return npiApi.update({ ...v, id: editing.project.id });
      return npiApi.create(v);
    },
    onSuccess: (newId) => {
      message.success(editing ? '项目信息已更新' : '新品会立项成功，请起草三档质量标准');
      setModalOpen(false);
      invalidate();
      if (newId) navigate(`/npi/projects/${newId}`);
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (r: ProjectListRow) => {
    setEditing(r);
    form.setFieldsValue({
      projectName: r.project.projectName,
      categoryId: r.project.categoryId,
      brand: r.project.brand || undefined,
      background: r.project.background || undefined,
      meetingAt: r.project.meetingAt ? dayjs(r.project.meetingAt) : undefined,
      attendees: r.project.attendees || undefined,
      targetListingDate: r.project.targetListingDate ? dayjs(r.project.targetListingDate) : undefined,
    });
    setModalOpen(true);
  };

  const columns: ColumnsType<ProjectListRow> = [
    {
      title: '项目编号',
      dataIndex: ['project', 'projectNo'],
      width: 150,
      render: (v: string) => <span style={{ fontFamily: 'monospace' }}>{v}</span>,
    },
    {
      title: '项目名称',
      dataIndex: ['project', 'projectName'],
      render: (v: string, r) => (
        <a onClick={() => navigate(`/npi/projects/${r.project.id}`)}>{v}</a>
      ),
    },
    { title: '品类', dataIndex: 'categoryName', width: 110 },
    { title: '品牌', dataIndex: ['project', 'brand'], width: 90 },
    {
      title: '状态',
      dataIndex: ['project', 'status'],
      width: 110,
      render: (v: string) => <Tag color={npiMeta(v)?.color}>{npiMeta(v)?.label || v}</Tag>,
    },
    {
      title: '寻源档位',
      dataIndex: ['project', 'targetGrade'],
      width: 90,
      render: (v?: string) =>
        v ? <Tag color={STD_GRADE_FULL[v]?.color}>{STD_GRADE_FULL[v]?.label.slice(0, 2) || v}</Tag> : '-',
    },
    { title: '定点供应商', dataIndex: 'chosenSupplierName', width: 200, render: (v?: string) => v || '-' },
    { title: '计划上市', dataIndex: ['project', 'targetListingDate'], width: 110, render: (v?: string) => v || '-' },
    { title: '发起人', dataIndex: 'initiatorName', width: 90 },
    {
      title: '操作',
      key: 'ops',
      width: 120,
      render: (_, r) => (
        <Space>
          <Button type="link" size="small" onClick={() => navigate(`/npi/projects/${r.project.id}`)}>
            进入
          </Button>
          {r.project.status === 'DRAFT' && (
            <AuthButton type="link" size="small" perm="npi:project:edit" onClick={() => openEdit(r)}>
              编辑
            </AuthButton>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="新品引入项目"
      extra={
        <AuthButton type="primary" perm="npi:project:create" onClick={openCreate}>
          新品会立项
        </AuthButton>
      }
    >
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => setQuery({ ...query, ...values, pageNo: 1 })}
      >
        <Form.Item name="projectName" label="项目名称">
          <Input allowClear placeholder="项目名称" />
        </Form.Item>
        <Form.Item name="status" label="阶段">
          <Select allowClear style={{ width: 140 }} placeholder="全部阶段" options={NPI_STATUS} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { searchForm.resetFields(); setQuery({ pageNo: 1, pageSize: 10 }); }}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<ProjectListRow>
        rowKey={(r) => r.project.id as string}
        loading={pageQuery.isLoading}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        scroll={{ x: 1250 }}
        pagination={{
          current: query.pageNo,
          pageSize: query.pageSize,
          total: Number(pageQuery.data?.total) || 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (pageNo, pageSize) => setQuery({ ...query, pageNo, pageSize }),
        }}
      />

      <Modal
        title={editing ? '编辑立项信息' : '新品会立项'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        // forceRender：openCreate/openEdit 打开前即操作 form
        forceRender
        width={640}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) =>
            saveMutation.mutate({
              projectName: v.projectName,
              categoryId: v.categoryId,
              brand: v.brand,
              background: v.background,
              attendees: v.attendees,
              meetingAt: v.meetingAt ? v.meetingAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
              targetListingDate: v.targetListingDate ? v.targetListingDate.format('YYYY-MM-DD') : undefined,
            })
          }
        >
          <Form.Item name="projectName" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
            <Input maxLength={200} placeholder="如 平阴玫瑰花茶新品引入" />
          </Form.Item>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="categoryId" label="拟引入品类" style={{ flex: 1 }} rules={[{ required: true, message: '请选择品类' }]}>
              <CategoryTreeSelect placeholder="选择品类（没有合适品类可直接在下拉底部新建）" />
            </Form.Item>
            <Form.Item name="brand" label="拟引入品牌" style={{ flex: 1 }}>
              <Input maxLength={100} />
            </Form.Item>
          </Space>
          <Form.Item name="background" label="立项背景 / 新品会结论">
            <Input.TextArea rows={3} maxLength={1000} />
          </Form.Item>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="meetingAt" label="新品会时间" style={{ flex: 1 }}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="targetListingDate" label="计划上市日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="attendees" label="参会人（品控/采购/老板）">
            <Input maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default ProjectList;
