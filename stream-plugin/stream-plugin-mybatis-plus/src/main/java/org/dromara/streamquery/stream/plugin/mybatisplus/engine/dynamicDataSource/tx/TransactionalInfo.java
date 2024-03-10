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
package org.dromara.streamquery.stream.plugin.mybatisplus.engine.dynamicDataSource.tx;

import lombok.Getter;
import lombok.Setter;

/**
 * 事务基础信息
 *
 * @author Hzh
 */
@Getter
@Setter
public class TransactionalInfo {

  /** 回滚异常 */
  Class<? extends Throwable>[] rollbackFor;

  /** 不回滚异常 */
  Class<? extends Throwable>[] noRollbackFor;

  /** 事务传播行为 */
  DsPropagation propagation;
}
