import React, { useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, TreeSelect } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import AuthButton from '@/components/AuthButton';
import { categoryApi, CategoryQuery, CategoryUpsert } from '@/api/category';
import type { Category, CategoryTree, LongId } from '@/types/auth';
import message from '@/utils/feedback';

interface FormValues extends Omit<CategoryUpsert, 'parentId'> {
  parentId?: LongId | number;
}

function buildTreeData(nodes: CategoryTree[]): { value: LongId; title: string; children?: never[] }[] {
  return nodes.map((n) => ({
    value: n.id,
    title: n.name,
    children: n.children?.length ? buildTreeData(n.children) : undefined,
  })) as never;
}

const CategoryList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<CategoryQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [form] = Form.useForm<FormValues>();

  const pageQuery = useQuery({
    queryKey: ['categories', query],
    queryFn: () => categoryApi.page(query),
  });

  const treeQuery = useQuery({
    queryKey: ['category-tree'],
    queryFn: () => categoryApi.tree(),
  });

  const saveMutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const payload: CategoryUpsert = { ...values, parentId: values.parentId ?? '0' };
      if (editing) {
        await categoryApi.update({ ...payload, id: editing.id });
      } else {
        await categoryApi.create(payload);
      }
    },
    onSuccess: () => {
      message.success(editing ? '更新成功' : '新增成功');
      setModalOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
      void queryClient.invalidateQueries({ queryKey: ['category-tree'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: LongId | number) => categoryApi.remove(id),
    onSuccess: () => {
      message.success('已删除（逻辑删除，审计留痕）');
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
      void queryClient.invalidateQueries({ queryKey: ['category-tree'] });
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId: '0', sort: 1, status: 1 });
    setModalOpen(true);
  };

  const openEdit = (record: Category) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const columns: ColumnsType<Category> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '品类编码', dataIndex: 'code', width: 160 },
    { title: '品类名称', dataIndex: 'name' },
    { title: '上级ID', dataIndex: 'parentId', width: 100 },
    { title: '排序', dataIndex: 'sort', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <Space>
          <AuthButton type="link" size="small" perm="master:category:edit" onClick={() => openEdit(record)}>
            编辑
          </AuthButton>
          <Popconfirm
            title="确认删除该品类？"
            description="逻辑删除，数据保留且审计留痕。"
            onConfirm={() => deleteMutation.mutate(record.id)}
          >
            <AuthButton type="link" size="small" danger perm="master:category:delete">
              删除
            </AuthButton>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="品类管理"
      extra={
        <AuthButton type="primary" perm="master:category:create" onClick={openCreate}>
          新增品类
        </AuthButton>
      }
    >
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => setQuery({ ...query, ...values, pageNo: 1 })}
      >
        <Form.Item name="name" label="名称">
          <Input placeholder="品类名称" allowClear />
        </Form.Item>
        <Form.Item name="code" label="编码">
          <Input placeholder="品类编码" allowClear />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            placeholder="全部"
            allowClear
            style={{ width: 120 }}
            options={[
              { value: 1, label: '启用' },
              { value: 0, label: '停用' },
            ]}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={() => { searchForm.resetFields(); setQuery({ pageNo: 1, pageSize: 10 }); }}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<Category>
        rowKey="id"
        loading={pageQuery.isLoading}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        locale={{ emptyText: pageQuery.isError ? '加载失败，请稍后重试' : '暂无品类数据' }}
        pagination={{
          current: query.pageNo,
          pageSize: query.pageSize,
          total: Number(pageQuery.data?.total) || 0,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (pageNo, pageSize) => setQuery({ ...query, pageNo, pageSize }),
        }}
      />

      <Modal
        title={editing ? '编辑品类' : '新增品类'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        // forceRender：openCreate/openEdit 打开前即操作 form
        forceRender
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          initialValues={{ parentId: '0', sort: 1, status: 1 }}
        >
          <Form.Item name="parentId" label="上级品类" rules={[{ required: true, message: '请选择上级品类' }]}>
            <TreeSelect
              allowClear
              treeData={[
                { value: '0', title: '顶级品类（无）' },
                ...(treeQuery.data ? buildTreeData(treeQuery.data) : []),
              ]}
              placeholder="选择上级品类"
            />
          </Form.Item>
          <Form.Item
            name="code"
            label="品类编码"
            rules={[
              { required: true, message: '请输入编码' },
              { pattern: /^[A-Z0-9_]+$/, message: '仅支持大写字母、数字、下划线' },
            ]}
          >
            <Input placeholder="如 TCM / NOURISHING" maxLength={50} />
          </Form.Item>
          <Form.Item name="name" label="品类名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default CategoryList;
