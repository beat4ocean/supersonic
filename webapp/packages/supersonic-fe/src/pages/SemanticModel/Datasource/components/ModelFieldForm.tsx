import React, { useState } from 'react';
import { Checkbox, Form, Input, Select, Space, Switch, Table, Tooltip } from 'antd';
import TableTitleTooltips from '../../components/TableTitleTooltips';
import { isUndefined } from 'lodash';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import SqlEditor from '@/components/SqlEditor';
import { ISemantic } from '../../data';
import {
  AGG_OPTIONS,
  DATE_FORMATTER,
  // DATE_OPTIONS,
  DIM_OPTIONS,
  EnumDataSourceType,
  EnumModelDataType,
  PARTITION_TIME_FORMATTER,
  TYPE_OPTIONS,
} from '../constants';
import styles from '../style.less';

const includeWords = ['_cnt_', '_sum_', '_count_', '_amount_', '_num_'];
const excludeWords = ['是否', '有无', '有没有', 'is_', 'if_'];
const excludeRegex = new RegExp(`^(?!.*(${excludeWords.join('|')}))`, 'i');
const keywordRegex =
  /(cnt|sum|count|amt|amount|price|interest|total|qty|qtys|quantity|fee|payment|balance|revenue|sales|orders|num|km|总|和|量|数|次|额|费|值|里程|公里)$/i;

type FieldItem = {
  expr?: string;
  bizName: string;
  dataType: string;
  name: string;
  type: EnumDataSourceType;
  classType: EnumModelDataType;
  comment?: string;
  agg?: string;
  checked?: number;
  dateFormat?: string;
  timeGranularity?: string;
  isTag?: number;
};
const { Search } = Input;
const FormItem = Form.Item;

type Props = {
  onSqlChange: (sql: string) => void;
  sql: string;
  tagObjectList: ISemantic.ITagObjectItem[];
  tagObjectId?: number;
  fields: FieldItem[];
  onFieldChange: (fieldName: string, data: Partial<FieldItem>) => void;
  onTagObjectChange?: (tagObjectId: number) => void;
};

const { Option } = Select;

const getCreateFieldName = (type: EnumDataSourceType) => {
  const isCreateName = [
    EnumDataSourceType.CATEGORICAL,
    EnumDataSourceType.TIME,
    EnumDataSourceType.PARTITION_TIME,
    EnumDataSourceType.FOREIGN,
  ].includes(type as EnumDataSourceType)
    ? 'isCreateDimension'
    : 'isCreateMetric';
  return isCreateName;
};

const ModelFieldForm: React.FC<Props> = ({
  fields,
  sql,
  tagObjectList,
  tagObjectId,
  onTagObjectChange,
  onFieldChange,
  onSqlChange,
}) => {
  const handleFieldChange = (record: FieldItem, fieldName: string, value: any) => {
    onFieldChange(record.bizName, {
      ...record,
      [fieldName]: value,
    });
  };
  const [filterValue, setFliterValue] = useState<string>();

  const columns = [
    {
      title: '字段名称',
      dataIndex: 'fieldName',
      width: 250,
    },
    {
      title: '字段类型',
      dataIndex: 'dataType',
      width: 100,
    },
    {
      title: '语义类型',
      dataIndex: 'type',
      width: 200,
      render: (_: any, record: FieldItem) => {
        // 自动推断类型逻辑
        if (!record.type) {
          let initialType=EnumDataSourceType.TIME;
          let classType: EnumModelDataType = EnumModelDataType.DIMENSION;
          let dateFormat: string | undefined;
          let agg: string | undefined;

          // 根据字段类型推断
          if (['DATETIME', 'DATE'].includes(record.dataType.toUpperCase())) {
            initialType = EnumDataSourceType.TIME;
            classType = EnumModelDataType.DIMENSION;
            dateFormat =
              record.dataType.toUpperCase() === 'DATETIME' ? 'yyyy-MM-dd HH:mm:ss' : 'yyyy-MM-dd';
          }
          // 根据字段名称推断
          else if (
            (excludeRegex.test(record.bizName.toLowerCase()) &&
              keywordRegex.test(record.bizName.toLowerCase())) ||
            (excludeRegex.test(record.bizName.toLowerCase()) &&
              includeWords.some((word) => record.bizName.toLowerCase().includes(word)))
          ) {
            initialType = EnumDataSourceType.MEASURES;
            classType = EnumModelDataType.MEASURES;
            agg = 'sum';
          }
          // 默认维度
          else {
            initialType = DIM_OPTIONS[0].value as EnumDataSourceType;
            classType = EnumModelDataType.DIMENSION;
          }

          if (initialType) {
            // 异步更新字段配置
            setTimeout(() => {
              onFieldChange(record.bizName, {
                ...record,
                type: initialType,
                classType,
                dateFormat,
                agg,
                name: record.comment || record.bizName || record.name,
                [getCreateFieldName(initialType!)]: true,
                checked: 1,
              });
            }, 0);
          }
        }

        const { type, classType } = record;
        const selectTypeValue = [EnumModelDataType.DIMENSION].includes(classType)
          ? classType
          : type;
        return (
          <Space>
            <Select
              placeholder="字段类型"
              value={selectTypeValue}
              allowClear
              onChange={(value) => {
                let defaultParams: any = {};
                if (value === EnumDataSourceType.MEASURES) {
                  defaultParams = {
                    agg: AGG_OPTIONS[0].value,
                    classType: EnumDataSourceType.MEASURES,
                    type: EnumDataSourceType.MEASURES,
                  };
                }
                // else if (value === EnumDataSourceType.TIME) {
                //   defaultParams = {
                //     dateFormat: DATE_FORMATTER[0],
                //     timeGranularity: 'day',
                //   };
                // }
                else if (value === EnumModelDataType.DIMENSION) {
                  defaultParams = {
                    type: DIM_OPTIONS[0].value,
                    classType: EnumModelDataType.DIMENSION,
                  };
                } else if (value === EnumDataSourceType.PRIMARY) {
                  defaultParams = {
                    type: EnumDataSourceType.PRIMARY,
                    classType: EnumModelDataType.IDENTIFIERS,
                  };
                } else {
                  defaultParams = {
                    type: value,
                    agg: undefined,
                    dateFormat: undefined,
                    timeGranularity: undefined,
                  };
                }
                const isCreateName = getCreateFieldName(defaultParams.type);
                const editState = !isUndefined(record[isCreateName])
                  ? !!record[isCreateName]
                  : true;
                const { name, bizName, comment } = record;
                const params = {
                  ...record,
                  type: value,
                  classType: value,
                  name: comment || bizName || name,
                  [isCreateName]: editState,
                  ...defaultParams,
                };
                onFieldChange(record.bizName, params);
              }}
              style={{ width: '100%' }}
            >
              {TYPE_OPTIONS.map((item) => (
                <Option key={item.label} value={item.value}>
                  {item.label}
                </Option>
              ))}
            </Select>
            {classType === EnumModelDataType.DIMENSION && (
              <Select
                placeholder="维度类型"
                value={type}
                onChange={(value) => {
                  // handleFieldChange(record, 'agg', value);
                  let defaultParams = {};
                  if (value === EnumDataSourceType.TIME) {
                    defaultParams = {
                      dateFormat: DATE_FORMATTER[0],
                      timeGranularity: 'day',
                    };
                  } else {
                    defaultParams = {
                      agg: undefined,
                      dateFormat: undefined,
                      timeGranularity: undefined,
                    };
                  }
                  const isCreateName = getCreateFieldName(value);
                  const editState = !isUndefined(record[isCreateName])
                    ? !!record[isCreateName]
                    : true;
                  const { name, bizName, comment } = record;
                  onFieldChange(record.bizName, {
                    ...record,
                    type: value,
                    name: comment || bizName || name,
                    [isCreateName]: editState,
                    ...defaultParams,
                  });
                }}
                defaultValue={DIM_OPTIONS[0].value}
                style={{ width: '100px' }}
              >
                {DIM_OPTIONS.map((item) => (
                  <Option key={item.value} value={item.value}>
                    {item.label}
                  </Option>
                ))}
              </Select>
            )}
          </Space>
        );
      },
    },
    {
      title: '扩展配置',
      dataIndex: 'extender',
      width: 350,
      render: (_: any, record: FieldItem) => {
        const { type } = record;
        if (type === EnumDataSourceType.MEASURES) {
          return (
            <Select
              placeholder="度量算子"
              value={record.agg}
              onChange={(value) => {
                handleFieldChange(record, 'agg', value);
              }}
              allowClear
              defaultValue={AGG_OPTIONS[0].value}
              style={{ width: '100%' }}
            >
              {AGG_OPTIONS.map((item) => (
                <Option key={item.value} value={item.value}>
                  {item.label}
                </Option>
              ))}
            </Select>
          );
        }
        if (process.env.SHOW_TAG) {
          if (type === EnumDataSourceType.CATEGORICAL) {
            const isTag = fields.find((field) => field.bizName === record.bizName)?.isTag;
            return (
              <Space>
                <Space>
                  <span>设为标签:</span>
                  <Switch
                    defaultChecked
                    size="small"
                    checked={!!isTag}
                    onChange={(value) => {
                      handleFieldChange(record, 'isTag', value);
                    }}
                  />
                  <Tooltip title="如果勾选，代表维度的取值都是一种“标签”，可用作对实体的圈选">
                    <ExclamationCircleOutlined />
                  </Tooltip>
                </Space>
              </Space>
            );
          }
        }
        if (process.env.SHOW_TAG) {
          if (type === EnumDataSourceType.CATEGORICAL) {
            const isTag = fields.find((field) => field.bizName === record.bizName)?.isTag;
            return (
              <Space>
                <Space>
                  <span>设为标签:</span>
                  <Switch
                    defaultChecked
                    size="small"
                    checked={!!isTag}
                    onChange={(value) => {
                      handleFieldChange(record, 'isTag', value);
                    }}
                  />
                  <Tooltip title="如果勾选，代表维度的取值都是一种“标签”，可用作对实体的圈选">
                    <ExclamationCircleOutlined />
                  </Tooltip>
                </Space>
              </Space>
            );
          }
        }
        if ([EnumDataSourceType.TIME, EnumDataSourceType.PARTITION_TIME].includes(type)) {
          const { dateFormat } = record;
          const dateFormatterOptions =
            type === EnumDataSourceType.PARTITION_TIME ? PARTITION_TIME_FORMATTER : DATE_FORMATTER;

          return (
            <Space direction="vertical">
              <Space>
                <span>时间格式:</span>
                <Select
                  placeholder="时间格式"
                  value={dateFormat}
                  onChange={(value) => {
                    handleFieldChange(record, 'dateFormat', value);
                  }}
                  defaultValue={dateFormatterOptions[0]}
                  style={{ minWidth: 250 }}
                >
                  {dateFormatterOptions.map((item) => (
                    <Option key={item} value={item}>
                      {item}
                    </Option>
                  ))}
                </Select>
                <Tooltip title="请选择数据库中时间字段对应格式">
                  <ExclamationCircleOutlined />
                </Tooltip>
              </Space>
              {/*<Space>*/}
              {/*  <span>时间粒度:</span>*/}
              {/*  <Select*/}
              {/*    placeholder="时间粒度"*/}
              {/*    value={timeGranularity === '' ? undefined : timeGranularity}*/}
              {/*    onChange={(value) => {*/}
              {/*      handleFieldChange(record, 'timeGranularity', value);*/}
              {/*    }}*/}
              {/*    defaultValue={timeGranularity === '' ? undefined : DATE_OPTIONS[0]}*/}
              {/*    style={{ minWidth: 180 }}*/}
              {/*    allowClear*/}
              {/*  >*/}
              {/*    {DATE_OPTIONS.map((item) => (*/}
              {/*      <Option key={item} value={item}>*/}
              {/*        {item}*/}
              {/*      </Option>*/}
              {/*    ))}*/}
              {/*  </Select>*/}
              {/*</Space>*/}
            </Space>
          );
        }
        return <></>;
      },
    },
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <TableTitleTooltips
            title="快速创建"
            tooltips="若勾选快速创建并填写名称，将会把该维度/指标直接创建到维度/指标列表"
          />
        </div>
      ),
      dataIndex: 'fastCreate',
      width: 350,
      render: (_: any, record: FieldItem) => {
        const { type } = record;
        if (
          ![
            EnumDataSourceType.PRIMARY,
            EnumDataSourceType.FOREIGN,
            EnumDataSourceType.CATEGORICAL,
            EnumDataSourceType.TIME,
            EnumDataSourceType.PARTITION_TIME,
            EnumDataSourceType.MEASURES,
          ].includes(type as EnumDataSourceType)
        ) {
          return <></>;
        }

        const isCreateName = getCreateFieldName(type);
        const editState = !isUndefined(record[isCreateName]) ? !!record[isCreateName] : true;

        return (
          <Space>
            <Checkbox
              checked={editState}
              onChange={(e) => {
                const value = e.target.checked;
                onFieldChange(record.bizName, {
                  ...record,
                  [isCreateName]: value ? 1 : 0,
                  checked: value ? 1 : 0,
                });
              }}
            />
            <Input
              className={!record.name && styles.dataSourceFieldsName}
              style={{ width: 300 }}
              value={record.name}
              disabled={!editState}
              onChange={(e) => {
                const value = e.target.value;
                onFieldChange(record.bizName, {
                  ...record,
                  name: value,
                  [isCreateName]: 1,
                });
              }}
              placeholder="请填写名称"
            />
          </Space>
        );
      },
    },
  ];

  const onSearch = (value: any) => {
    setFliterValue(value);
  };

  const tableData = filterValue
    ? fields.filter((item) => {
        return item.bizName.includes(filterValue);
      }) || []
    : fields;

  return (
    <>
      <div style={{ marginBottom: 10 }}>
        <Search
          allowClear
          // className={styles.search}
          style={{ width: 250 }}
          placeholder="请输入英文名称进行搜索"
          onSearch={onSearch}
        />
      </div>

      <Table<FieldItem>
        dataSource={tableData}
        columns={columns}
        rowKey="bizName"
        virtual
        pagination={false}
        scroll={{ y: 500 }}
      />
      <FormItem
        style={{ marginTop: 40, marginBottom: 60 }}
        name="filterSql"
        label={<span style={{ fontSize: 14 }}>过滤SQL</span>}
        tooltip="主要用于词典导入场景, 对维度值进行过滤 格式: field1 = 'xxx' and field2 = 'yyy'"
      >
        <SqlEditor height={'150px'} value={sql} onChange={onSqlChange} />
      </FormItem>
    </>
  );
};

export default ModelFieldForm;
