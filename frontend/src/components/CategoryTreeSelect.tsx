import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, TreeSelect } from 'antd';
import type { TreeSelectProps } from 'antd';
import { PlusOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { categoryApi } from '@/api/category';
import { BizError } from '@/api/request';
import { useAuthStore } from '@/store/authStore';
import type { CategoryTree, LongId } from '@/types/auth';
import message from '@/utils/feedback';

/**
 * 品类树选择器（全系统统一入口：NPI 立项、产品档案、检验标准模板等）。
 *
 * 交互约定（需求：不依赖用户先会“品类管理”页）：
 * 1. 下拉面板底部固定“+ 新建品类”入口；
 * 2. 点击后在下拉面板内联展开创建框（编码、名称、上级品类、排序），
 *    默认上级取当前已选品类，未选时默认为顶级品类；
 * 3. 提交走现有 POST /api/v1/master/categories，成功后刷新共享品类树缓存，
 *    自动选中新建品类并收起下拉，无需刷新页面；
 * 4. 编码重复/校验失败时，错误信息内联展示在创建框内（该请求静默全局提示）。
 *
 * 说明：创建框不使用嵌套 Modal——双层 Modal 下内层弹窗的焦点会被外层
 * 对话框的焦点管理抢走，导致下拉/输入无法聚焦；内联面板交互更顺。
 */

/** 顶级品类虚拟节点（与后端“parentId=0 即顶级”约定一致，保持字符串） */
const ROOT_VALUE = '0';

interface CreateFormValues {
  parentId: LongId;
  code: string;
  name: string;
  sort?: number;
}

function buildTreeData(nodes: CategoryTree[]): NonNullable<TreeSelectProps['treeData']> {
  return nodes.map((n) => ({
    value: n.id,
    title: n.name,
    children: n.children?.length ? buildTreeData(n.children) : undefined,
  }));
}

interface FlatNode {
  id: string;
  name: string;
  depth: number;
}

/** 把品类树拍平为带层级缩进的列表（用于内联创建框中选择上级品类，避免弹层中再套弹层） */
function flatten(nodes: CategoryTree[], depth = 0, acc: FlatNode[] = []): FlatNode[] {
  nodes.forEach((n) => {
    acc.push({ id: n.id, name: n.name, depth });
    if (n.children?.length) flatten(n.children, depth + 1, acc);
  });
  return acc;
}

/** 内联上级品类选择：纯 div 列表，点击即选，高亮当前项 */
const ParentCategoryPicker: React.FC<{
  value?: string;
  onChange?: (v: string) => void;
  options: FlatNode[];
}> = ({ value, onChange, options }) => (
  <div
    role="listbox"
    aria-label="上级品类"
    style={{
      maxHeight: 132,
      overflowY: 'auto',
      border: '1px solid #d9d9d9',
      borderRadius: 6,
    }}
  >
    {options.map((o) => {
      const active = value === o.id;
      return (
        <div
          key={o.id}
          role="option"
          aria-selected={active}
          onClick={() => onChange?.(o.id)}
          style={{
            padding: '4px 8px',
            paddingLeft: 8 + o.depth * 16,
            cursor: 'pointer',
            background: active ? '#e6f4ff' : '#fff',
            color: active ? '#1677ff' : 'inherit',
            fontWeight: active ? 600 : 400,
            whiteSpace: 'nowrap',
          }}
        >
          {o.depth > 0 ? '└ ' : ''}
          {o.name}
        </div>
      );
    })}
  </div>
);

/** 从请求异常中提取后端中文错误信息（BizError 或 axios HTTP 错误） */
function resolveErrorMessage(e: unknown): string {
  if (e instanceof BizError && e.message) return e.message;
  if (e && typeof e === 'object' && 'response' in e) {
    const data = (e as { response?: { data?: { message?: string } } }).response?.data;
    if (data?.message) return data.message;
  }
  if (e instanceof Error && e.message) return e.message;
  return '创建失败，请稍后重试';
}

export type CategoryTreeSelectProps = Omit<TreeSelectProps, 'treeData' | 'popupRender'>;

const CategoryTreeSelect: React.FC<CategoryTreeSelectProps> = (props) => {
  const { value, onChange } = props;
  const queryClient = useQueryClient();

  // 与各列表页共用同一份 react-query 缓存，新建后一处刷新处处生效
  const catTree = useQuery({ queryKey: ['category-tree'], queryFn: () => categoryApi.tree() });
  const treeData = useMemo(() => buildTreeData(catTree.data || []), [catTree.data]);
  // 上级品类候选：顶级 + 拍平后的品类树
  const parentOptions = useMemo<FlatNode[]>(
    () => [{ id: ROOT_VALUE, name: '顶级品类（无）', depth: 0 }, ...flatten(catTree.data || [])],
    [catTree.data],
  );

  // 无“品类新增”权限的角色不显示入口（后端 @PreAuthorize 仍会二次拦截）
  const canCreate = useAuthStore((s) => s.hasPerm('master:category:create'));

  // 下拉开合状态仅用于“收起后复位内联创建框”，不控制 open——
  // rc-select 在受控 open 下，ESC/外部点击等内部关闭路径不一定回调，
  // 会出现内外状态不一致（面板实际收起却再也点不开），故保持非受控
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [createForm] = Form.useForm<CreateFormValues>();

  // 下拉收起时复位内联创建框
  useEffect(() => {
    if (!dropdownOpen) {
      setCreating(false);
      setSubmitError('');
    }
  }, [dropdownOpen]);

  /**
   * 模拟一次“点击弹层外部”的完整鼠标序列，让 rc-select 走它自己的
   * 外部点击关闭路径（内部状态与可见性保持一致，后续仍能正常点开）
   */
  const closeDropdownLikeOutsideClick = () => {
    window.setTimeout(() => {
      const opts: MouseEventInit = { bubbles: true, cancelable: true, view: window };
      document.body.dispatchEvent(new MouseEvent('mousedown', opts));
      document.body.dispatchEvent(new MouseEvent('mouseup', opts));
      document.body.dispatchEvent(new MouseEvent('click', opts));
    }, 0);
  };

  const createMutation = useMutation({
    mutationFn: (values: CreateFormValues) =>
      // silent：错误内联展示在创建框内，不再弹全局 message
      categoryApi.create(
        {
          parentId: values.parentId || ROOT_VALUE,
          code: values.code,
          name: values.name,
          sort: values.sort,
          status: 1,
        },
        { silent: true },
      ),
  });

  const startCreate = () => {
    setSubmitError('');
    createForm.resetFields();
    // 默认上级：当前已选品类；当前为空/顶级虚拟值时默认顶级
    const current = value && value !== ROOT_VALUE ? (value as LongId) : ROOT_VALUE;
    createForm.setFieldsValue({ parentId: current, sort: 1 });
    setCreating(true);
  };

  const cancelCreate = () => {
    setCreating(false);
    setSubmitError('');
  };

  const handleCreate = async (values: CreateFormValues) => {
    setSubmitError('');
    try {
      const newId = await createMutation.mutateAsync(values);
      // 等待品类树刷新完成再回填，保证选中项能立刻显示名称
      await queryClient.invalidateQueries({ queryKey: ['category-tree'] });
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
      setCreating(false);
      closeDropdownLikeOutsideClick();
      // 自动选中新建品类（外层通常是 antd Form.Item，onChange 即写表单值；
      // 简化回调签名，antd Form.Item 不依赖第三参数 extra）
      (onChange as ((value: LongId, label?: React.ReactNode) => void) | undefined)?.(newId, values.name);
      message.success(`品类「${values.name}」已创建并选中`);
    } catch (e) {
      setSubmitError(resolveErrorMessage(e));
    }
  };

  return (
    <TreeSelect
      showSearch
      treeNodeFilterProp="title"
      {...props}
      onOpenChange={(open) => setDropdownOpen(open)}
      treeData={treeData}
      popupRender={(menu) => (
        <div>
          {menu}
          {canCreate && (
            <div
              style={{
                borderTop: '1px solid #f0f0f0',
                padding: creating ? '8px 8px 4px' : '6px 8px',
                background: '#fff',
              }}
            >
              {creating ? (
                <Form
                  form={createForm}
                  layout="vertical"
                  size="small"
                  onFinish={handleCreate}
                  style={{ marginTop: 2 }}
                >
                  {submitError && (
                    <Alert
                      type="error"
                      showIcon
                      style={{ marginBottom: 8 }}
                      message="品类创建失败"
                      description={submitError}
                    />
                  )}
                  <Form.Item
                    name="code"
                    label="编码"
                    rules={[
                      { required: true, message: '请输入编码' },
                      { pattern: /^[A-Z0-9_]+$/, message: '仅支持大写字母、数字、下划线' },
                    ]}
                  >
                    <Input placeholder="如 TCM / NOURISHING" maxLength={50} autoComplete="off" />
                  </Form.Item>
                  <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入品类名称' }]}>
                    <Input placeholder="如 中药饮片" maxLength={50} autoComplete="off" />
                  </Form.Item>
                  <Form.Item name="parentId" label="上级品类（点击选择）" rules={[{ required: true, message: '请选择上级品类' }]}>
                    <ParentCategoryPicker options={parentOptions} />
                  </Form.Item>
                  <Form.Item name="sort" label="排序" style={{ marginBottom: 8 }}>
                    <InputNumber min={0} max={9999} style={{ width: '100%' }} placeholder="数值越小越靠前" />
                  </Form.Item>
                  <div style={{ textAlign: 'right' }}>
                    <Button size="small" icon={<CloseOutlined />} onClick={cancelCreate} style={{ marginRight: 8 }}>
                      取消
                    </Button>
                    <Button
                      size="small"
                      type="primary"
                      htmlType="submit"
                      icon={<CheckOutlined />}
                      loading={createMutation.isPending}
                    >
                      保存并选中
                    </Button>
                  </div>
                </Form>
              ) : (
                <Button block type="dashed" size="small" icon={<PlusOutlined />} onClick={startCreate}>
                  新建品类
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    />
  );
};

export default CategoryTreeSelect;
