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
package org.dromara.streamquery.stream.plugin.mybatisplus.engine.dynamicDataSource.datasource.enums;

import lombok.Getter;

/**
 * 目前支持的XA数据源
 *
 * @author <a href="mailto:312290710@qq.com">jiazhifeng</a>
 */
@Getter
public enum XADataSourceEnum {
  /** mysql */
  MYSQL("com.mysql.cj.jdbc.MysqlXADataSource"),
  /** oracle */
  ORACLE("oracle.jdbc.xa.client.OracleXADataSource"),
  /** postgresql */
  POSTGRE_SQL("org.postgresql.xa.PGXADataSource"),
  /** h2 */
  H2("org.h2.jdbcx.JdbcDataSource");

  /** xa数据源类名 */
  private final String xaDriverClassName;

  /**
   * 构造方法
   *
   * @param xaDriverClassName
   */
  XADataSourceEnum(String xaDriverClassName) {
    this.xaDriverClassName = xaDriverClassName;
  }

  /**
   * 是否包含
   *
   * @param xaDataSourceClassName xa数据源类名
   * @return boolean 包含
   */
  public static boolean contains(String xaDataSourceClassName) {
    for (XADataSourceEnum item : values()) {
      if (item.getXaDriverClassName().equals(xaDataSourceClassName)) {
        return true;
      }
    }
    return false;
  }
}
