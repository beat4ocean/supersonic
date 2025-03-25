import { ISemantic } from '../data';
import { SemanticNodeType, StatusEnum } from '../enum';
import { TreeGraphData } from '@antv/g6-core';
import { DisplayMode } from './components/GraphLegendVisibleModeItem';

export const typeConfigs = {
  datasource: {
    type: 'circle',
    size: 10,
  },
};

export const getDimensionChildren = (
  dimensions: ISemantic.IDimensionItem[],
  dataSourceNodeId: string,
  limit: number = 999,
) => {
  const dimensionChildrenList = dimensions.reduce(
    (dimensionChildren: any[], dimension: ISemantic.IDimensionItem) => {
      const { id } = dimension;
      dimensionChildren.push({
        ...dimension,
        nodeType: SemanticNodeType.DIMENSION,
        legendType: dataSourceNodeId,
        id: `${dataSourceNodeId}-${SemanticNodeType.DIMENSION}-${id}`,
        uid: id,
        style: {
          lineWidth: 2,
          fill: '#f0f7ff',
          stroke: '#a6ccff',
        },
      });
      return dimensionChildren;
    },
    [],
  );
  return dimensionChildrenList.slice(0, limit);
};

export const getMetricChildren = (
  metrics: ISemantic.IMetricItem[],
  dataSourceNodeId: string,
  limit: number = 999,
) => {
  const metricsChildrenList = metrics.reduce(
    (metricsChildren: any[], metric: ISemantic.IMetricItem) => {
      const { id } = metric;
      metricsChildren.push({
        ...metric,
        nodeType: SemanticNodeType.METRIC,
        legendType: dataSourceNodeId,
        id: `${dataSourceNodeId}-${SemanticNodeType.METRIC}-${id}`,
        uid: id,
        style: {
          lineWidth: 2,
          fill: '#f0f7ff',
          stroke: '#a6ccff',
        },
      });
      return metricsChildren;
    },
    [],
  );
  return metricsChildrenList.slice(0, limit);
};

export const formatterRelationData = (params: {
  dataSourceList: ISemantic.IDomainSchemaRelaList;
  limit?: number;
  displayMode?: DisplayMode;
  showDataSourceId?: string[];
}): TreeGraphData[] => {
  const { displayMode, dataSourceList, limit, showDataSourceId } = params;
  const contentType = displayMode?.contentType;
  const statusFilter = displayMode?.statusFilter;

  const relationData = dataSourceList.reduce(
    (relationList: TreeGraphData[], item: ISemantic.IDomainSchemaRelaItem) => {
      const { model, dimensions, metrics } = item;
      const { id, status, name } = model;

      // 如果启用了状态过滤，且当前模型状态不是启用状态，则跳过
      if (statusFilter === StatusEnum.ONLINE && status !== StatusEnum.ONLINE) {
        return relationList;
      }

      const dataSourceNodeId = `${SemanticNodeType.DATASOURCE}-${id}`;
      let childrenList = [];

      if (contentType === SemanticNodeType.METRIC) {
        childrenList = getMetricChildren(metrics, dataSourceNodeId, limit);
      }
      else if (contentType === SemanticNodeType.DIMENSION) {
        childrenList = getDimensionChildren(dimensions, dataSourceNodeId, limit);
      }
      else if (contentType === 'MODEL_ONLY') {
        // 仅模型名称，不添加子节点
        childrenList = [];
      }
      else if (!contentType || contentType === '') {
        const dimensionList = getDimensionChildren(dimensions, dataSourceNodeId, limit);
        const metricList = getMetricChildren(metrics, dataSourceNodeId, limit);
        childrenList = [...dimensionList, ...metricList];
      }

      // 根据状态设置不同的样式
      let nodeStyle = {
        lineWidth: 2,
        fill: '#BDEFDB',
        stroke: '#5AD8A6',
      };

      // 未启用状态使用灰色
      if (status !== StatusEnum.ONLINE) {
        nodeStyle = {
          lineWidth: 2,
          fill: '#f5f5f5',
          stroke: '#d9d9d9',
        };
      }

      if (!showDataSourceId || showDataSourceId.includes(dataSourceNodeId)) {
        relationList.push({
          ...model,
          name: name,
          statusText: status === StatusEnum.ONLINE ? '已启用' : '未启用',
          legendType: dataSourceNodeId,
          id: dataSourceNodeId,
          uid: id,
          nodeType: SemanticNodeType.DATASOURCE,
          size: 40,
          children: [...childrenList],
          style: nodeStyle,
        });
      }
      return relationList;
    },
    [],
  );
  return relationData;
};

export const loopNodeFindDataSource: any = (node: any) => {
  const { model, parent } = node;
  if (model?.nodeType === SemanticNodeType.DATASOURCE) {
    return model;
  }
  const parentNode = parent?._cfg;
  if (parentNode) {
    return loopNodeFindDataSource(parentNode);
  }
  return false;
};

export const getNodeConfigByType = (nodeData: any, defaultConfig = {}) => {
  const { nodeType, statusText } = nodeData;
  const labelCfg = { style: { fill: '#3c3c3c' } };

  switch (nodeType) {
    case SemanticNodeType.DATASOURCE: {
      return {
        ...defaultConfig,
        labelCfg: { position: 'bottom', ...labelCfg },
        labels: [
          {
            position: 'center',
            content: statusText || '',
            style: {
              fill: statusText === '已启用' ? '#52c41a' : '#bfbfbf',
              fontSize: 12,
              fontWeight: 500
            }
          }
        ],
        size: [80, 40],
      };
    }
    case SemanticNodeType.DIMENSION:
      return {
        ...defaultConfig,
        labelCfg: { position: 'right', ...labelCfg },
        size: [10, 10],
      };
    case SemanticNodeType.METRIC:
      return {
        ...defaultConfig,
        style: {
          lineWidth: 2,
          fill: '#fffbe6',
          stroke: '#ffe58f',
        },
        labelCfg: { position: 'right', ...labelCfg },
        size: [10, 10],
      };
    default:
      return {
        ...defaultConfig,
        size: [10, 10],
      };
  }
};

export const flatGraphDataNode = (graphData: any[]) => {
  return graphData.reduce((nodeList: any[], item: any) => {
    const { children } = item;
    if (Array.isArray(children)) {
      nodeList.push(...children);
    }
    return nodeList;
  }, []);
};

interface Node {
  label: string;
  children?: Node[];
}

export const findNodesByLabel = (query: string, nodes: Node[]): Node[] => {
  const result: Node[] = [];

  for (const node of nodes) {
    let match = false;
    let children: Node[] = [];

    // 如果节点的label包含查询字符串，我们将其标记为匹配
    if (node.label.includes(query)) {
      match = true;
    }

    // 我们还需要在子节点中进行搜索
    if (node.children) {
      children = findNodesByLabel(query, node.children);
      if (children.length > 0) {
        match = true;
      }
    }

    // 如果节点匹配或者其子节点匹配，我们将其添加到结果中
    if (match) {
      result.push({ ...node, children });
    }
  }

  return result;
};
