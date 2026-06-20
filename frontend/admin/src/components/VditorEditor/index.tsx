import 'vditor/dist/index.css';
import { Button, Input, Modal } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState } from 'react';

interface VditorEditorProps {
  value?: string;
  onChange?: (md: string) => void;
}

const VditorEditor: React.FC<VditorEditorProps> = ({ value, onChange }) => {
  const [previewOpen, setPreviewOpen] = useState(false);
  const previewRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (previewOpen && previewRef.current) {
      import('vditor').then((mod) => {
        const Vditor = mod.default || mod;
        Vditor.preview(previewRef.current!, value || '', {
          hljs: { style: 'github' },
        });
      });
    }
  }, [previewOpen, value]);

  return (
    <>
      <Input.TextArea
        value={value}
        onChange={(e) => onChange?.(e.target.value)}
        rows={18}
        style={{ fontFamily: 'monospace' }}
      />
      <Button
        type="link"
        icon={<EyeOutlined />}
        onClick={() => setPreviewOpen(true)}
        style={{ padding: 0, marginTop: 4 }}
      >
        预览
      </Button>
      <Modal
        title="预览"
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width={900}
      >
        <div ref={previewRef} style={{ minHeight: 200 }} />
      </Modal>
    </>
  );
};

export default VditorEditor;
