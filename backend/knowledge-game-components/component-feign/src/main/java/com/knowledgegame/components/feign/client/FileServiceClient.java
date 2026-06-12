package com.knowledgegame.components.feign.client;

import com.knowledgegame.core.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 文件服务 Feign Client
 * <p>
 * 按提供方维度组织，app/admin 共用
 */
@FeignClient(name = "knowledge-game-file")
public interface FileServiceClient {

    /**
     * 生成上传凭证（M2M 接口）
     *
     * @param userId   上传者用户 ID
     * @param count    凭证允许上传的文件数量
     * @param basePath 文件存储路径标识（如 ip-series、avatar）
     * @return 凭证 token
     */
    @PostMapping("/api/file/internal/credential")
    Result<String> generateCredential(@RequestParam("userId") Long userId,
                                      @RequestParam("count") int count,
                                      @RequestParam("basePath") String basePath);
}
