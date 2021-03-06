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

package org.openo.sdno.localsiteservice.rest.cpe;

import java.util.Arrays;

import org.openo.baseservice.remoteservice.exception.ServiceException;
import org.openo.baseservice.roa.util.restclient.RestfulParametes;
import org.openo.baseservice.roa.util.restclient.RestfulResponse;
import org.openo.sdno.exception.HttpCode;
import org.openo.sdno.framework.container.resthelper.RestfulProxy;
import org.openo.sdno.framework.container.util.JsonUtil;
import org.openo.sdno.framework.container.util.UuidUtils;
import org.openo.sdno.overlayvpn.errorcode.ErrorCode;
import org.openo.sdno.overlayvpn.model.v2.cpe.SbiDeviceInfo;
import org.openo.sdno.overlayvpn.result.ResultRsp;

import mockit.Mock;
import mockit.MockUp;

public class MockCpeOnlineRestfulProxy extends MockUp<RestfulProxy> {

    private static RestfulResponse restfulResponse = new RestfulResponse();
    static {
        restfulResponse.setStatus(HttpCode.RESPOND_OK);
    }

    @Mock
    public static RestfulResponse post(String uri, RestfulParametes restParametes) throws ServiceException {
        SbiDeviceInfo sbiDeviceInfo = new SbiDeviceInfo();
        sbiDeviceInfo.setUuid(UuidUtils.createUuid());
        ResultRsp<SbiDeviceInfo> resultRsp = new ResultRsp<SbiDeviceInfo>(ErrorCode.OVERLAYVPN_SUCCESS);
        resultRsp.setSuccessed(Arrays.asList(sbiDeviceInfo));
        restfulResponse.setResponseJson(JsonUtil.toJson(resultRsp));
        return restfulResponse;
    }

    @Mock
    public static RestfulResponse delete(String uri, RestfulParametes restParametes) throws ServiceException {
        ResultRsp<String> resultRsp = new ResultRsp<String>(ErrorCode.OVERLAYVPN_SUCCESS);
        restfulResponse.setResponseJson(JsonUtil.toJson(resultRsp));
        return restfulResponse;
    }

}
