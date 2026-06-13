import { request } from 'umi';
import { getUserInfo } from '@/utils/token';

/** 上传凭证响应 */
export interface UploadCredentialResponse {
  token: string;
  uploadUrl: string;
}

/** 文件上传响应 */
interface FileUploadResponse {
  fileId: string;
  url: string;
}

/**
 * 获取上传凭证
 * @param bizType 业务类型（如 IP_SERIES）
 * @param count   凭证允许上传的文件数量，默认 1
 */
export async function getUploadCredential(
  bizType: string,
  count: number = 1,
): Promise<UploadCredentialResponse> {
  return request<UploadCredentialResponse>(
    `/api/admin/upload-credential?bizType=${bizType}&count=${count}`,
    { method: 'GET' },
  );
}

/**
 * 直传文件到文件服务
 * 使用 raw fetch（不走 UmiJS request 拦截器），因为文件上传使用凭证 token 鉴权而非 JWT
 *
 * @param token     上传凭证 token
 * @param uploadUrl 上传地址
 * @param file      要上传的文件
 * @param userId    当前用户 ID
 * @returns 上传成功后文件的完整 URL
 */
export async function uploadFile(
  token: string,
  uploadUrl: string,
  file: File,
  userId: number,
): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(uploadUrl, {
    method: 'POST',
    headers: {
      'X-Upload-Token': token,
      'X-User-Id': String(userId),
    },
    body: formData,
  });

  if (!response.ok) {
    // 尝试解析文件服务返回的 JSON 格式错误信息，提取可读消息
    let errorMsg = '上传失败';
    try {
      const errorBody: { message?: string } = await response.json();
      errorMsg = errorBody.message || errorMsg;
    } catch {
      const text = await response.text().catch(() => '');
      if (text) errorMsg = text;
    }
    throw new Error(errorMsg);
  }

  const result: { code: number; data: FileUploadResponse; message?: string } =
    await response.json();

  if (result.code !== 200 || !result.data) {
    throw new Error(result.message || '上传失败');
  }

  // 基于标准 URL 解析拼接完整 URL（相对路径相对于 uploadUrl 解析）
  return new URL(result.data.url, uploadUrl).href;
}
