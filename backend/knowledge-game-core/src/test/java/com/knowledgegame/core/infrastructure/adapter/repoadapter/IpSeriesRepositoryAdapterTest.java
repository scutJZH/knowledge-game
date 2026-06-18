package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IpSeriesRepositoryAdapter 单元测试
 * <p>
 * 覆盖 REQ-86 ISSUE-3：findByConditions 参数化排序（白名单校验 + 默认 createdAt DESC）+ code 模糊搜索。
 * <p>
 * Mock 策略：用 ArgumentCaptor&lt;PageRequest&gt; 捕获 adapter 内部调用
 * ipSeriesJpaRepository.findAll(spec, pageRequest) 时传入的 PageRequest，
 * 断言 captor.getValue().getSort() 的字段名与方向。
 */
@ExtendWith(MockitoExtension.class)
class IpSeriesRepositoryAdapterTest {

    @Mock
    private IpSeriesJpaRepository ipSeriesJpaRepository;

    @InjectMocks
    private IpSeriesRepositoryAdapter adapter;

    /**
     * sort=null 走默认 createdAt DESC
     */
    @Test
    @DisplayName("findByConditions sort=null 时使用默认 createdAt DESC")
    void findByConditions_sortNull_fallbackToCreatedAtDesc() {
        when(ipSeriesJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, 0, 20);

        PageRequest captured = capturePageRequest();
        Sort sort = captured.getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
    }

    /**
     * sort=code&order=asc 走 code ASC
     */
    @Test
    @DisplayName("findByConditions sort=code&order=asc 时使用 code ASC")
    void findByConditions_sortCodeAsc_returnsCodeAscSort() {
        when(ipSeriesJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null,
                new SortField("code", SortField.Direction.ASC), 0, 20);

        PageRequest captured = capturePageRequest();
        Sort.Order order = captured.getSort().getOrderFor("code");
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    /**
     * sort=foo 非法字段抛 BusinessException(400)
     */
    @Test
    @DisplayName("findByConditions sort=foo 时抛 BusinessException(400)")
    void findByConditions_sortInvalid_throws400() {
        SortField invalid = new SortField("foo", SortField.Direction.DESC);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adapter.findByConditions(null, null, null, invalid, 0, 20));

        assertEquals(400, ex.getCode());
    }

    /**
     * sort=name&order=desc 走 name DESC（覆盖非默认字段 + DESC 方向）
     */
    @Test
    @DisplayName("findByConditions sort=name&order=desc 时使用 name DESC")
    void findByConditions_sortNameDesc_returnsNameDescSort() {
        when(ipSeriesJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null,
                new SortField("name", SortField.Direction.DESC), 0, 20);

        PageRequest captured = capturePageRequest();
        Sort.Order order = captured.getSort().getOrderFor("name");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    /**
     * code 参数非空时能正常走通查询链路（不抛 NPE）。
     * code 模糊匹配谓词的实际过滤效果由 IpSeriesListSortIntegrationTest 端到端验证。
     */
    @Test
    @DisplayName("findByConditions code 非空时不抛异常并完成调用")
    void findByConditions_codeParamPassed_completesWithoutException() {
        when(ipSeriesJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, "IP001", null, null, 0, 20);

        verify(ipSeriesJpaRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    /**
     * 捕获 adapter 传给 findAll 的 PageRequest 参数
     */
    private PageRequest capturePageRequest() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(ipSeriesJpaRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    /**
     * 构造空 Page，仅用于让 findAll 返回非 null 结果
     */
    @SuppressWarnings("unchecked")
    private Page<IpSeriesPO> emptyPage() {
        return (Page<IpSeriesPO>) org.mockito.Mockito.mock(Page.class);
    }
}
