/*
 * Copyright 2017 Huawei Technologies Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openo.sdno.localsiteservice.rest.site;

import java.util.Arrays;
import java.util.List;

import org.openo.baseservice.remoteservice.exception.ServiceException;
import org.openo.baseservice.roa.util.restclient.RestfulParametes;
import org.openo.baseservice.roa.util.restclient.RestfulResponse;
import org.openo.sdno.exception.HttpCode;
import org.openo.sdno.framework.container.resthelper.RestfulProxy;
import org.openo.sdno.framework.container.util.JsonUtil;
import org.openo.sdno.localsiteservice.model.inf.InterfaceModel;
import org.openo.sdno.overlayvpn.errorcode.ErrorCode;
import org.openo.sdno.overlayvpn.result.ResultRsp;

import mockit.Mock;
import mockit.MockUp;

public class MockInterfaceRestfulProxy extends MockUp<RestfulProxy> {

    private static RestfulResponse restfulResponse = new RestfulResponse();
    static {
        restfulResponse.setStatus(HttpCode.RESPOND_OK);
    }

    @Mock
    public static RestfulResponse get(String uri, RestfulParametes restParametes) throws ServiceException {
        InterfaceModel interfaceModel = new InterfaceModel();
        interfaceModel.setName("TestInterface");
        ResultRsp<List<InterfaceModel>> resultRsp = new ResultRsp<List<InterfaceModel>>(ErrorCode.OVERLAYVPN_SUCCESS);
        resultRsp.setData(Arrays.asList(interfaceModel));
        restfulResponse.setResponseJson(JsonUtil.toJson(resultRsp));
        return restfulResponse;
    }

}
