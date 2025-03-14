import { Tabs, Radio } from 'antd';
import React, { useRef, useEffect, useState } from 'react';
import { useModel } from '@umijs/max';
import PermissionSection from './Permission/PermissionSection';
import TagObjectTable from '../Insights/components/TagObjectTable';
import TermTable from '../components/Term/TermTable';
import OverView from './OverView';
import styles from './style.less';
import SemanticGraphCanvas from '../SemanticGraphCanvas';
import View from '../View';

type Props = {
  activeKey: string;
  onMenuChange?: (menuKey: string) => void;
};
const DomainManagerTab: React.FC<Props> = ({ activeKey, onMenuChange }) => {
  const initState = useRef<boolean>(false);
  const defaultTabKey = 'metric';

  const domainModel = useModel('SemanticModel.domainData');
  const modelModel = useModel('SemanticModel.modelData');

  const { selectDomainId, selectDomain: domainData } = domainModel;
  const { selectModelId, modelList } = modelModel;

  useEffect(() => {
    initState.current = false;
  }, [selectModelId]);

  const tabItem = [
    {
      label: '数据集管理',
      key: 'overview',
      hidden: !!domainData?.parentId,
      children: <View />,
    },
    {
      label: '模型管理',
      key: 'modelManage',
      children: (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <OverView modelList={modelList} />
          <div style={{ width: '100%', height: 600 }}>
            {/* 添加 key 强制重新渲染 */}
            <SemanticGraphCanvas key={selectDomainId} />
          </div>
        </div>
      ),
    },
    {
      label: '标签对象管理',
      key: 'tagObjectManage',
      hidden: !!!process.env.SHOW_TAG ? true : !!domainData?.parentId,
      children: <TagObjectTable />,
    },
    {
      label: '术语管理',
      key: 'termManage',
      hidden: !!domainData?.parentId,
      children: <TermTable />,
    },
    {
      label: '权限管理',
      key: 'permissonSetting',
      hidden: !!domainData?.parentId,
      children: <PermissionSection permissionTarget={'domain'} />,
    },
  ].filter((item) => {
    if (item.hidden) {
      return false;
    }
    if (domainData?.hasEditPermission) {
      return true;
    }
    return item.key !== 'permissonSetting';
  });

  const getActiveKey = () => {
    const key = activeKey || defaultTabKey;
    const tabItems = tabItem;
    const tabItemsKeys = tabItems.map((item) => item.key);
    if (!tabItemsKeys.includes(key)) {
      return tabItemsKeys[0];
    }
    return key;
  };

  return (
    <div>
      <Tabs
        className={styles.tab}
        items={tabItem}
        activeKey={getActiveKey()}
        size="large"
        onChange={(menuKey: string) => {
          onMenuChange?.(menuKey);
        }}
      />
    </div>
  );
};

export default DomainManagerTab;
