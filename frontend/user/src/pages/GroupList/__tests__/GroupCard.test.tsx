import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import GroupCard from '../GroupCard';

const baseGroup = {
  id: 1, name: '测试群组', description: null,
  avatarFileId: null, avatarUrl: null,
  ownerId: 100, joinPolicy: 'OPEN' as const,
  myRole: 'MEMBER' as const, memberCount: 5,
  createdAt: 1, updatedAt: 1,
};

describe('GroupCard', () => {
  it('无头像时显示首字渐变方块', () => {
    render(<MemoryRouter><GroupCard group={baseGroup} onClick={() => {}} /></MemoryRouter>);
    expect(screen.getByText('测')).toBeInTheDocument();
  });

  it('有 avatarUrl 时显示图片', () => {
    const g = { ...baseGroup, avatarUrl: 'https://example.com/avatar.png' };
    render(<MemoryRouter><GroupCard group={g} onClick={() => {}} /></MemoryRouter>);
    const img = screen.getByAltText('');
    expect(img).toHaveAttribute('src', 'https://example.com/avatar.png');
  });

  it('OWNER 角色显示 role-tag-owner 样式', () => {
    const g = { ...baseGroup, myRole: 'OWNER' as const };
    render(<MemoryRouter><GroupCard group={g} onClick={() => {}} /></MemoryRouter>);
    expect(screen.getByText('OWNER').className).toContain('role-tag-owner');
  });

  it('ADMIN 角色显示 role-tag-admin 样式', () => {
    const g = { ...baseGroup, myRole: 'ADMIN' as const };
    render(<MemoryRouter><GroupCard group={g} onClick={() => {}} /></MemoryRouter>);
    expect(screen.getByText('ADMIN').className).toContain('role-tag-admin');
  });

  it('MEMBER 角色显示 role-tag-member 样式', () => {
    const g = { ...baseGroup, myRole: 'MEMBER' as const };
    render(<MemoryRouter><GroupCard group={g} onClick={() => {}} /></MemoryRouter>);
    expect(screen.getByText('MEMBER').className).toContain('role-tag-member');
  });

  it('显示成员数和角色中文名', () => {
    render(<MemoryRouter><GroupCard group={baseGroup} onClick={() => {}} /></MemoryRouter>);
    expect(screen.getByText('5 成员 · 成员')).toBeInTheDocument();
  });
});
