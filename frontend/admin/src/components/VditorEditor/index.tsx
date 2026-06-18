import 'vditor/dist/index.css';
import { message } from 'antd';
import { useEffect, useRef } from 'react';

interface VditorEditorProps {
  value?: string;
  onChange?: (md: string) => void;
}

const VditorEditor: React.FC<VditorEditorProps> = ({ value, onChange }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const vditorRef = useRef<any>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    import('vditor').then((VditorModule) => {
      const Vditor = VditorModule.default || VditorModule;
      vditorRef.current = new Vditor(container, {
        mode: 'wysiwyg',
        height: 400,
        cache: { enable: false },
        preview: { hljs: { style: 'github' } },
        upload: {
          handler: (_files: File[]) => {
            return '';
          },
        },
        value: value || '',
        input: (md: string) => {
          onChange?.(md);
        },
      });
    }).catch(() => {
      message.error('Markdown 编辑器加载失败，请刷新重试');
    });

    return () => {
      if (vditorRef.current) {
        try {
          vditorRef.current.destroy();
        } catch {
          // DOM 可能已被 React 移除，忽略 destroy 异常
        }
        vditorRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (vditorRef.current && value !== undefined) {
      vditorRef.current.setValue(value, true);
    }
  }, [value]);

  return <div ref={containerRef} />;
};

export default VditorEditor;
