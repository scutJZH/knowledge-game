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
    let vditor: any = null;

    import('vditor').then((VditorModule) => {
      const Vditor = VditorModule.default || VditorModule;
      vditor = new Vditor(containerRef.current!, {
        mode: 'wysiwyg',
        height: 400,
        cache: { enable: false },
        preview: { hljs: { style: 'github' } },
        upload: {
          handler: (files: File[]) => {
            // 凭证式上传，由上层通过 fileUpload service 处理
            return '';
          },
        },
        value: value || '',
        after: () => {
          vditorRef.current = vditor;
        },
        input: (md: string) => {
          onChange?.(md);
        },
      });
    }).catch(() => {
      message.error('Markdown 编辑器加载失败，请刷新重试');
    });

    return () => {
      vditor?.destroy();
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
