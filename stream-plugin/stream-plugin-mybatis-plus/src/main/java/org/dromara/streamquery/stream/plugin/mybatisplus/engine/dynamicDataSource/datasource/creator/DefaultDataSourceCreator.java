/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dromara.streamquery.stream.plugin.mybatisplus.engine.dynamicDataSource.datasource.creator;

import javax.sql.DataSource;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.streamquery.stream.plugin.mybatisplus.engine.configuration.DataSourceProperty;
import org.dromara.streamquery.stream.plugin.mybatisplus.engine.dynamicDataSource.datasource.enums.SeataMode;
import org.dromara.streamquery.stream.plugin.mybatisplus.engine.dynamicDataSource.datasource.toolkit.CryptoUtils;
import org.dromara.streamquery.stream.plugin.mybatisplus.engine.dynamicDataSource.datasource.toolkit.DsStrUtils;
import org.dromara.streamquery.stream.plugin.mybatisplus.engine.dynamicDataSource.event.DataSourceInitEvent;

import java.util.List;

/**
 * 数据源创建器
 *
 * @author TaoYu
 * @since 2.3.0
 */
@Slf4j
@Setter
public class DefaultDataSourceCreator {

  private List<DataSourceCreator> creators;

  /** 是否懒加载数据源 */
  private Boolean lazy = false;
  /** /** 是否使用p6spy输出，默认不输出 */
  private Boolean p6spy = false;
  /** 是否使用开启seata，默认不开启 */
  private Boolean seata = false;
  /** seata使用模式，默认AT */
  private SeataMode seataMode = SeataMode.AT;
  /** 全局默认publicKey */
  private String publicKey = CryptoUtils.DEFAULT_PUBLIC_KEY_STRING;

  private DataSourceInitEvent dataSourceInitEvent;

  /**
   * 创建数据源
   *
   * @param dataSourceProperty 数据源参数
   * @return 数据源
   */
  public DataSource createDataSource(DataSourceProperty dataSourceProperty) {
    DataSourceCreator dataSourceCreator = null;
    for (DataSourceCreator creator : this.creators) {
      if (creator.support(dataSourceProperty)) {
        dataSourceCreator = creator;
        break;
      }
    }
    if (dataSourceCreator == null) {
      throw new IllegalStateException(
          "creator must not be null,please check the DataSourceCreator");
    }
    String propertyPublicKey = dataSourceProperty.getPublicKey();
    if (DsStrUtils.isEmpty(propertyPublicKey)) {
      dataSourceProperty.setPublicKey(publicKey);
    }
    Boolean propertyLazy = dataSourceProperty.getLazy();
    if (propertyLazy == null) {
      dataSourceProperty.setLazy(lazy);
    }
    if (dataSourceInitEvent != null) {
      dataSourceInitEvent.beforeCreate(dataSourceProperty);
    }
    DataSource dataSource = dataSourceCreator.createDataSource(dataSourceProperty);
    if (dataSourceInitEvent != null) {
      dataSourceInitEvent.afterCreate(dataSource);
    }
    return dataSource;
  }
}
