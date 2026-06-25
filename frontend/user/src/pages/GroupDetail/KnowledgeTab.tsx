import { useEffect, useState } from 'react';
import { Card, Spin, Empty, message, List, Typography, Modal, Pagination } from 'antd';
import { FolderOutlined } from '@ant-design/icons';
import { listQuestionsByCategory } from '@/services/group-api';
import { apiClient } from '@/services/api-client';
import type { QuestionListResponse, QuestionPageResponse } from '@/types/group';

const { Text } = Typography;

interface CategoryNode {
  id: number;
  name: string;
  children?: CategoryNode[];
}

export default function KnowledgeTab() {
  const [categories, setCategories] = useState<CategoryNode[]>([]);
  const [catLoading, setCatLoading] = useState(true);
  const [selectedCat, setSelectedCat] = useState<CategoryNode | null>(null);
  const [questions, setQuestions] = useState<QuestionListResponse[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [qLoading, setQLoading] = useState(false);
  const [detailOpen, setDetailOpen] = useState<QuestionListResponse | null>(null);

  useEffect(() => {
    apiClient.get<never, CategoryNode[]>('/knowledge-categories/tree')
      .then((data) => setCategories(data as unknown as CategoryNode[]))
      .catch(() => message.error('加载分类失败'))
      .finally(() => setCatLoading(false));
  }, []);

  const fetchQuestions = async (catId: number, p: number) => {
    setQLoading(true);
    try {
      const result = await listQuestionsByCategory(catId, p) as unknown as QuestionPageResponse;
      setQuestions(result.content);
      setTotal(result.totalElements);
    } catch { message.error('加载题目失败'); }
      finally { setQLoading(false); }
  };

  const selectCategory = async (cat: CategoryNode) => {
    setSelectedCat(cat);
    setPage(1);
    await fetchQuestions(cat.id, 1);
  };

  const handlePageChange = (p: number) => {
    setPage(p);
    if (selectedCat) fetchQuestions(selectedCat.id, p);
  };

  const flatten = (cats: CategoryNode[]): CategoryNode[] => {
    let result: CategoryNode[] = [];
    for (const cat of cats) {
      result.push(cat);
      if (cat.children) result = result.concat(flatten(cat.children));
    }
    return result;
  };

  if (catLoading) return <Spin style={{ display: 'block', margin: '24px auto' }} />;

  if (selectedCat) {
    return (
      <div>
        <a onClick={() => setSelectedCat(null)} style={{ display: 'block', marginBottom: 12 }}>&larr; 返回分类列表</a>
        <Text strong style={{ fontSize: 16 }}>{selectedCat.name}</Text>
        {qLoading ? <Spin /> : (
          <>
            <List
              dataSource={questions}
              renderItem={(q) => (
                <List.Item onClick={() => setDetailOpen(q)} style={{ cursor: 'pointer' }}>
                  <List.Item.Meta title={q.title} />
                </List.Item>
              )}
            />
            {total > 20 && (
              <Pagination
                current={page}
                total={total}
                pageSize={20}
                onChange={handlePageChange}
                size="small"
                style={{ textAlign: 'center', marginTop: 12 }}
              />
            )}
          </>
        )}
        <Modal title="题目详情" open={!!detailOpen} onCancel={() => setDetailOpen(null)} footer={null} width={600}>
          {detailOpen && (
            <>
              <Text>{detailOpen.fullText}</Text>
              <div style={{ marginTop: 16, padding: 12, background: '#f6ffed', borderRadius: 8 }}>
                <Text strong>答案：</Text>
                <div dangerouslySetInnerHTML={{ __html: detailOpen.answer }} />
              </div>
              <div style={{ marginTop: 12 }}>
                <a>查看相关知识点 &rarr;</a>
              </div>
            </>
          )}
        </Modal>
      </div>
    );
  }

  if (!categories.length) return <Empty description="暂无分类" />;

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 12 }}>
      {flatten(categories).map((cat) => (
        <Card key={cat.id} hoverable onClick={() => selectCategory(cat)}>
          <FolderOutlined style={{ fontSize: 24, color: '#8b5cf6', marginBottom: 8 }} />
          <div>{cat.name}</div>
        </Card>
      ))}
    </div>
  );
}
