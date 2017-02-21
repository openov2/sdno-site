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

package org.openo.sdno.localsiteservice.rest.inventory;

import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.openo.baseservice.remoteservice.exception.ServiceException;
import org.openo.sdno.localsiteservice.moco.inventorydao.MockNetworkElementInvDao;
import org.openo.sdno.localsiteservice.springtest.SpringTest;
import org.openo.sdno.overlayvpn.brs.model.NetworkElementMO;
import org.springframework.beans.factory.annotation.Autowired;

import mockit.Mocked;

public class NetworkElementRoaResourceTest extends SpringTest {

    @Autowired
    private NetworkElementRoaResource networkElementRoaResource;

    @Mocked
    private HttpServletRequest httpRequest;

    @Before
    public void setUp() {
        new MockNetworkElementInvDao();
    }

    @Test
    public void queryTest() throws ServiceException {
        List<NetworkElementMO> neList = networkElementRoaResource.query(httpRequest, "[\"NeId\"]");
        assertTrue("neName".equals(neList.get(0).getName()));
    }

    @Test(expected = ServiceException.class)
    public void neUuidsQueryTest() throws ServiceException {
        networkElementRoaResource.query(httpRequest, null);
    }

}
