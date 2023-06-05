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
package org.dromara.streamquery.stream.plugin.mybatisplus.annotation.pojo.po.inner;

import lombok.Data;
import org.dromara.streamquery.stream.plugin.mybatisplus.engine.annotation.GenerateMapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

/**
 * AddressInfo
 *
 * @author KamToHung
 */
@Data
@GenerateMapper
public class AddressInfo {

  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String address;

  @Data
  public static class InnerAddressInfo {
    private String id;
  }
}
