/**
 * 共享分页类型，供所有管理页面复用
 */
export interface PageResult<T> {
  content: T[];
  totalElements: number;
  pageNumber: number;
  pageSize: number;
  totalPages: number;
}
