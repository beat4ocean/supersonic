import { Segmented, Space, Radio } from 'antd';
import React, { useState, useEffect } from 'react';
import { SemanticNodeType, StatusEnum } from '../../enum';
import styles from '../style.less';

export interface DisplayMode {
  statusFilter: boolean | number; // true/StatusEnum.ONLINE表示仅显示启用，false表示全部
  contentType: string | SemanticNodeType; // 内容类型：''/DIMENSION/METRIC/MODEL_ONLY
}

type Props = {
  value?: DisplayMode;
  onChange?: (value: DisplayMode) => void;
  [key: string]: any;
};

const GraphLegendVisibleModeItem: React.FC<Props> = ({ value, onChange }) => {
  const [displayMode, setDisplayMode] = useState<DisplayMode>(
    value || { statusFilter: StatusEnum.ONLINE, contentType: '' }
  );

  useEffect(() => {
    if (value) {
      setDisplayMode(value);
    }
  }, [value]);

  const handleStatusChange = (statusFilter: boolean | number) => {
    const newMode = { ...displayMode, statusFilter };
    setDisplayMode(newMode);
    onChange?.(newMode);
  };

  const handleContentTypeChange = (contentType: string | SemanticNodeType) => {
    const newMode = { ...displayMode, contentType };
    setDisplayMode(newMode);
    onChange?.(newMode);
  };

  return (
    <div className={styles.graphLegendVisibleModeItem}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Radio.Group
          size="small"
          value={displayMode.statusFilter}
          onChange={(e) => handleStatusChange(e.target.value)}
          buttonStyle="solid"
          style={{ display: 'flex', width: '100%' }}
        >
          <Radio.Button value={false} style={{ flex: 1, textAlign: 'center' }}>全部</Radio.Button>
          <Radio.Button value={StatusEnum.ONLINE} style={{ flex: 1, textAlign: 'center' }}>仅已启用</Radio.Button>
        </Radio.Group>
        
        <Segmented
          size="small"
          block={true}
          value={displayMode.contentType}
          onChange={(value) => handleContentTypeChange(value as string | SemanticNodeType)}
          options={[
            {
              value: '',
              label: '全部',
            },
            {
              value: SemanticNodeType.DIMENSION,
              label: '仅维度',
            },
            {
              value: SemanticNodeType.METRIC,
              label: '仅指标',
            },
            {
              value: 'MODEL_ONLY',
              label: '仅模型名称',
            },
          ]}
        />
      </Space>
    </div>
  );
};

export default GraphLegendVisibleModeItem;
