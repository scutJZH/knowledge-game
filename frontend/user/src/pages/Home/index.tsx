import { Typography, Card } from 'antd';

const { Title, Paragraph, Text } = Typography;

const futurePages = [
  '首页（群组选择）',
  '知识点分类树',
  '秒判游戏',
  'Boss 挑战',
  '串联答题',
  '翻牌奖励',
  '结算页面',
  '图鉴收集',
  '盲盒抽取',
  '卡牌详情',
  '保底记录',
  '个人中心',
  '签到',
];

function Home() {
  return (
    <div>
      <Title level={2}>Knowledge Game</Title>
      <Paragraph>REQ-26 脚手架已就位，等待后续需求填充业务页面</Paragraph>
      <Card title="后续页面入口预览">
        {futurePages.map((page) => (
          <Text key={page} type="secondary" style={{ display: 'inline-block', margin: '4px 12px' }}>
            {page}
          </Text>
        ))}
      </Card>
    </div>
  );
}

export default Home;
