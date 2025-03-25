import React from 'react';
import {
  ControlOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  SearchOutlined,
  OneToOneOutlined,
} from '@ant-design/icons';
import { FloatButton, Tooltip, Input } from 'antd';
import GraphLegendVisibleModeItem, { DisplayMode } from '../GraphLegendVisibleModeItem';
import { SemanticNodeType } from '../../../enum';
import styles from './style.less';

const { Search } = Input;

type Props = {
  graph: any;
  onSearch?: (text: string) => void;
  onShowTypeChange?: (displayMode: DisplayMode) => void;
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  onAutoZoom?: () => void;
};

function zoomGraph(graph, ratio: number) {
  const width = graph.get('width');
  const height = graph.get('height');
  const centerX = width / 2;
  const centerY = height / 2;
  graph.zoom(ratio, { x: centerX, y: centerY });
}

const ControlToolBar: React.FC<Props> = ({
  graph,
  onSearch,
  onShowTypeChange,
  onZoomIn,
  onZoomOut,
  onAutoZoom,
}) => {
  const sensitivity = 0.1; // 设置缩放灵敏度，值越小，缩放越不敏感，默认值为 1
  const zoomOutRatio = 1 - sensitivity;
  const zoomInRatio = 1 + sensitivity;

  return (
    <div className={styles.controlToolBarPanel}>
      <FloatButton.Group type="primary" style={{ left: 260 }}>
        <Tooltip
          overlayClassName={styles.overlayClassName}
          title={
            <Input.Search
              placeholder="输入搜索内容"
              onSearch={(value) => {
                onSearch?.(value);
              }}
              enterButton
              style={{ width: 250 }}
            />
          }
          placement="right"
        >
          <FloatButton icon={<SearchOutlined />} description="搜索" />
        </Tooltip>

        <Tooltip
          overlayClassName={styles.overlayClassName}
          title={
            <GraphLegendVisibleModeItem
              onChange={(displayMode: DisplayMode) => {
                onShowTypeChange?.(displayMode);
              }}
            />
          }
          placement="right"
        >
          <FloatButton icon={<ControlOutlined />} description="模式" />
        </Tooltip>

        <FloatButton
          icon={<ZoomInOutlined />}
          description="放大"
          onClick={() => {
            zoomGraph(graph, zoomInRatio);
            onZoomIn?.();
          }}
        />
        <FloatButton
          icon={<ZoomOutOutlined />}
          description="缩小"
          onClick={() => {
            zoomGraph(graph, zoomOutRatio);
            onZoomOut?.();
          }}
        />
        <FloatButton
          icon={<OneToOneOutlined />}
          description="重置"
          onClick={() => {
            onAutoZoom?.();
          }}
        />
      </FloatButton.Group>
    </div>
  );
};

export default ControlToolBar;
