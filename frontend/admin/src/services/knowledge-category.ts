// @ts-ignore — UmiJS 构建时生成类型，IDE 无法解析虚拟路径
import { request } from '@umijs/max';

/** 分类树节点 */
export interface CategoryTreeNode {
  id: number;
  parentId: number | null;
  name: string;
  status: string;
  iconFileId: number | null;
  iconUrl: string | null;
  color: string | null;
  sortOrder: number;
  children?: CategoryTreeNode[];
}

/** 分类详情 */
export interface CategoryDetail {
  id: number;
  parentId: number | null;
  name: string;
  description: string | null;
  iconFileId: number | null;
  iconUrl: string | null;
  color: string | null;
  coverImageFileId: number | null;
  coverImageUrl: string | null;
  sortOrder: number;
  status: string;
  createdAt: number;
  updatedAt: number;
}

/** 创建表单数据 */
export interface CategoryFormData {
  parentId?: number | null;
  name: string;
  description?: string;
  iconFileId?: number;
  color?: string;
  coverImageFileId?: number;
  sortOrder?: number;
  status?: string;
}

/**
 * 更新分类字段（三态语义）
 * - 字段缺失（undefined）：不更新
 * - 字段为 null：清空（仅可清空字段：description / iconFileId / color / coverImageFileId）
 * - 字段有值：更新
 */
export interface CategoryUpdateData {
  parentId?: number | null;
  name?: string;
  description?: string | null;
  iconFileId?: number | null;
  color?: string | null;
  coverImageFileId?: number | null;
  sortOrder?: number;
  status?: string;
}

/** 批量排序项 */
export interface BatchSortItem {
  id: number;
  sortOrder: number;
}

/** AntD TreeSelect 的 treeData 节点类型 */
export interface TreeDataNode {
  title: string;
  value: number;
  key: number;
  children?: TreeDataNode[];
}

/** 将 CategoryTreeNode 递归转为 AntD TreeSelect 的 treeData 格式 */
export function convertToTreeData(nodes: CategoryTreeNode[]): TreeDataNode[] {
  return nodes.map((node) => ({
    title: node.name,
    value: node.id,
    key: node.id,
    children: node.children ? convertToTreeData(node.children) : undefined,
  }));
}

/**
 * 与 convertToTreeData 相同，但过滤 INACTIVE 节点（题库等业务表单禁止选已停用分类）。
 * 注意：保留包含 ACTIVE 子节点的 INACTIVE 父级链路不被截断不易处理且场景罕见，
 * 因此本函数对 INACTIVE 节点直接整体剪枝（含其所有子节点），不保留子树。
 */
export function convertToTreeDataActiveOnly(nodes: CategoryTreeNode[]): TreeDataNode[] {
  return nodes
    .filter((node) => node.status === 'ACTIVE')
    .map((node) => ({
      title: node.name,
      value: node.id,
      key: node.id,
      children: node.children ? convertToTreeDataActiveOnly(node.children) : undefined,
    }));
}

/**
 * 将分类树转为 id → 全路径 "一级 / 二级 / 三级" 的 Map，供 TreeSelect tagRender 使用
 */
export function buildCategoryPathMap(nodes: CategoryTreeNode[]): Map<number, string> {
  const map = new Map<number, string>();
  const walk = (list: CategoryTreeNode[], ancestors: string[]) => {
    for (const node of list) {
      const path = [...ancestors, node.name].join(' / ');
      map.set(node.id, path);
      if (node.children && node.children.length > 0) {
        walk(node.children, [...ancestors, node.name]);
      }
    }
  };
  walk(nodes, []);
  return map;
}

/** 获取分类树 */
export async function getTree(): Promise<CategoryTreeNode[]> {
  return request('/api/admin/knowledge-categories/tree');
}

/** 获取分类详情 */
export async function getById(id: number): Promise<CategoryDetail> {
  return request(`/api/admin/knowledge-categories/${id}`);
}

/** 创建分类 */
export async function create(data: CategoryFormData): Promise<CategoryDetail> {
  return request('/api/admin/knowledge-categories', {
    method: 'POST',
    data,
  });
}

/** 更新分类（支持三态语义：undefined / null / 值） */
export async function update(id: number, data: CategoryUpdateData): Promise<CategoryDetail> {
  return request(`/api/admin/knowledge-categories/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 移动分类 */
export async function move(id: number, newParentId: number | null): Promise<CategoryDetail> {
  return request(`/api/admin/knowledge-categories/${id}/move`, {
    method: 'PUT',
    data: { newParentId },
  });
}

/** 删除分类（递归移入回收站，含子分类） */
export async function deleteCategory(id: number): Promise<void> {
  return request(`/api/admin/knowledge-categories/${id}`, {
    method: 'DELETE',
  });
}

/** 批量排序 */
export async function batchSort(items: BatchSortItem[]): Promise<void> {
  return request('/api/admin/knowledge-categories/batch-sort', {
    method: 'PUT',
    data: { items },
  });
}
