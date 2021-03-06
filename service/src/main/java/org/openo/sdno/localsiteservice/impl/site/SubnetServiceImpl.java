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

package org.openo.sdno.localsiteservice.impl.site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.openo.baseservice.remoteservice.exception.ServiceException;
import org.openo.sdno.framework.container.util.JsonUtil;
import org.openo.sdno.framework.container.util.UuidUtils;
import org.openo.sdno.localsiteservice.dao.ModelDataDao;
import org.openo.sdno.localsiteservice.dao.SiteModelDao;
import org.openo.sdno.localsiteservice.inf.site.SubnetBdInfoService;
import org.openo.sdno.localsiteservice.inf.site.SubnetService;
import org.openo.sdno.localsiteservice.sbi.site.SubnetSbiService;
import org.openo.sdno.localsiteservice.sbi.site.TemplateSbiService;
import org.openo.sdno.overlayvpn.brs.invdao.LogicalTernminationPointInvDao;
import org.openo.sdno.overlayvpn.brs.model.LogicalTernminationPointMO;
import org.openo.sdno.overlayvpn.brs.model.NetworkElementMO;
import org.openo.sdno.overlayvpn.dao.common.InventoryDao;
import org.openo.sdno.overlayvpn.errorcode.ErrorCode;
import org.openo.sdno.overlayvpn.inventory.sdk.util.InventoryDaoUtil;
import org.openo.sdno.overlayvpn.model.common.enums.ActionStatus;
import org.openo.sdno.overlayvpn.model.v2.result.ComplexResult;
import org.openo.sdno.overlayvpn.model.v2.subnet.NbiSubnetModel;
import org.openo.sdno.overlayvpn.model.v2.subnet.SbiSubnetNetModel;
import org.openo.sdno.overlayvpn.result.ResultRsp;
import org.openo.sdno.util.ip.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation class of Snat Service.<br>
 * 
 * @author
 * @version SDNO 0.5 2017-1-19
 */
@Service
public class SubnetServiceImpl implements SubnetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubnetServiceImpl.class);

    @Autowired
    private SubnetSbiService sbiService;

    @Autowired
    private SubnetBdInfoService bdInfoService;

    @Autowired
    private TemplateSbiService templateSbiSrevice;

    @Autowired
    private SiteModelDao siteModelDao;

    @Autowired
    private LogicalTernminationPointInvDao ltpInvDao;

    @Override
    public ResultRsp<NbiSubnetModel> query(HttpServletRequest req, String subnetUuid) throws ServiceException {
        ResultRsp<NbiSubnetModel> queryResultRsp =
                (new ModelDataDao<NbiSubnetModel>()).query(NbiSubnetModel.class, subnetUuid);
        if(!queryResultRsp.isValid()) {
            LOGGER.error("Query Subnet Model failed, need to check database");
            return new ResultRsp<>(ErrorCode.OVERLAYVPN_FAILED);
        }

        ResultRsp<SbiSubnetNetModel> queryNetModelResultRsp = queryBySubnetId(subnetUuid);
        if(!queryNetModelResultRsp.isSuccess()) {
            LOGGER.error("Query Subnet Net Model failed, need to check database");
            return new ResultRsp<>(ErrorCode.OVERLAYVPN_FAILED);
        }

        if(queryNetModelResultRsp.isValid()) {
            queryResultRsp.getData().setVni(queryNetModelResultRsp.getData().getVni());
        }

        return queryResultRsp;
    }

    @Override
    public ComplexResult<NbiSubnetModel> batchQuery(String name, String tenantId, String siteId, String pageNum,
            String pageSize) throws ServiceException {
        Map<String, Object> filterMap = new HashMap<>();

        if(StringUtils.isNotBlank(name)) {
            filterMap.put("name", Arrays.asList(name));
        }

        if(StringUtils.isNotBlank(tenantId)) {
            filterMap.put("tenantId", Arrays.asList(tenantId));
        }

        if(StringUtils.isNotBlank(siteId)) {
            filterMap.put("siteId", Arrays.asList(siteId));
        }

        InventoryDao<NbiSubnetModel> subnetModelDao = new InventoryDaoUtil<NbiSubnetModel>().getInventoryDao();
        ResultRsp<List<NbiSubnetModel>> queryResultRsp =
                subnetModelDao.queryByFilter(NbiSubnetModel.class, JsonUtil.toJson(filterMap), null);

        List<NbiSubnetModel> subnetModelList = queryResultRsp.getData();
        for(NbiSubnetModel subnetModel : subnetModelList) {
            ResultRsp<SbiSubnetNetModel> queryNetModelResultRsp = queryBySubnetId(subnetModel.getUuid());
            if(!queryNetModelResultRsp.isSuccess()) {
                LOGGER.error("Query Subnet Net Model failed");
                throw new ServiceException("Query Subnet Net Model failed");
            }
            subnetModel.setVni(queryNetModelResultRsp.getData().getVni());
        }

        ComplexResult<NbiSubnetModel> complexResult = new ComplexResult<>();
        complexResult.setData(subnetModelList);
        complexResult.setTotal(subnetModelList.size());

        return complexResult;
    }

    @Override
    public ResultRsp<NbiSubnetModel> create(HttpServletRequest req, NbiSubnetModel subnetModel)
            throws ServiceException {

        // Fill Port Uuids
        fillPortUUids(subnetModel);

        NetworkElementMO siteGateway = siteModelDao.getSiteGateway(subnetModel.getSiteId());
        boolean isIpv6 = "IPV6".equalsIgnoreCase(siteGateway.getAccessIPVersion());

        checkSubnetModelParameter(subnetModel, isIpv6);

        // Insert Subnet Mode into database
        subnetModel.setActionState(ActionStatus.CREATING.getName());
        (new ModelDataDao<NbiSubnetModel>()).insert(subnetModel);

        // Create Subnet Net Model
        SbiSubnetNetModel subnetNetModel = buildCreateSubnetNetModel(subnetModel, siteGateway, isIpv6);
        subnetNetModel.setActionState(ActionStatus.CREATING.getName());
        ResultRsp<SbiSubnetNetModel> insertResultRsp = (new ModelDataDao<SbiSubnetNetModel>()).insert(subnetNetModel);
        if(!insertResultRsp.isValid()) {
            LOGGER.error("SbiSubnetNetModel insert failed");
            throw new ServiceException("SbiSubnetNetModel insert failed");
        }

        ResultRsp<SbiSubnetNetModel> createResultRsp = sbiService.create(subnetNetModel);
        if(!createResultRsp.isValid()) {
            LOGGER.error("SbiSubnetNetModel create failed");
            throw new ServiceException("SbiSubnetNetModel create failed");
        }

        SbiSubnetNetModel newSbiSubnetNetModel = insertResultRsp.getData();
        newSbiSubnetNetModel.setNetworkId(createResultRsp.getData().getNetworkId());
        newSbiSubnetNetModel.setActionState(ActionStatus.NORMAL.getName());

        ResultRsp<SbiSubnetNetModel> updateResultRsp =
                (new ModelDataDao<SbiSubnetNetModel>()).update(newSbiSubnetNetModel, "networkId,actionState");
        if(!updateResultRsp.isValid()) {
            LOGGER.error("Subnet net model update failed, need to check database");
            throw new ServiceException("SubnetNetModel update failed");
        }

        String gatewayInterface = bdInfoService.getSubnetBdInfo(subnetModel.getVni(), subnetNetModel.getNeId());
        subnetModel.setActionState(ActionStatus.NORMAL.getName());
        subnetModel.setGatewayInterface(gatewayInterface);
        if(null == subnetModel.getCidrBlockSize()) {
            subnetModel.setCidrBlockSize(0);
        }

        return (new ModelDataDao<NbiSubnetModel>()).update(subnetModel, "actionState,gatewayInterface,cidrBlockSize");
    }

    @Override
    public ResultRsp<NbiSubnetModel> delete(HttpServletRequest req, NbiSubnetModel subnetModel)
            throws ServiceException {

        ResultRsp<SbiSubnetNetModel> queryResultRsp = queryBySubnetId(subnetModel.getUuid());
        if(!queryResultRsp.isValid()) {
            LOGGER.error("Subnet net model query failed or not exist, need to check database");
            throw new ServiceException("SubnetNetModel query failed or not exist");
        }

        SbiSubnetNetModel subnetNetModel = queryResultRsp.getData();

        ResultRsp<SbiSubnetNetModel> deleteResultRsp = sbiService.delete(subnetNetModel);
        if(!deleteResultRsp.isSuccess()) {
            LOGGER.error("SbiSubnetNetModel delete from driver failed");
            throw new ServiceException("SbiSubnetNetModel delete from driver failed");
        }

        ResultRsp<String> deleteNetModelResult =
                (new ModelDataDao<SbiSubnetNetModel>()).delete(SbiSubnetNetModel.class, subnetNetModel.getUuid());
        if(!deleteNetModelResult.isSuccess()) {
            LOGGER.error("SbiSubnetNetModel delete from database failed");
            throw new ServiceException("SbiSubnetNetModel delete from database failed");
        }

        ResultRsp<String> deleteModelResult =
                (new ModelDataDao<NbiSubnetModel>()).delete(NbiSubnetModel.class, subnetModel.getUuid());
        if(!deleteModelResult.isSuccess()) {
            LOGGER.error("NbiSubnetModel delete from database failed");
            throw new ServiceException("NbiSubnetModel delete from database failed");
        }

        return new ResultRsp<>(ErrorCode.OVERLAYVPN_SUCCESS, subnetModel);
    }

    @Override
    public ResultRsp<NbiSubnetModel> update(HttpServletRequest req, NbiSubnetModel subnetModel)
            throws ServiceException {

        subnetModel.setActionState(ActionStatus.UPDATING.getName());
        (new ModelDataDao<NbiSubnetModel>()).update(subnetModel, "actionState");

        ResultRsp<SbiSubnetNetModel> queryResultRsp = queryBySubnetId(subnetModel.getUuid());
        if(!queryResultRsp.isValid()) {
            LOGGER.error("SbiSubnetNetModel query failed or not exist");
            throw new ServiceException("SbiSubnetNetModel query failed or not exist");
        }

        SbiSubnetNetModel subnetNetModel = queryResultRsp.getData();
        subnetNetModel.setDescription(subnetModel.getDescription());

        ResultRsp<SbiSubnetNetModel> updateResultRsp = sbiService.update(subnetNetModel);
        if(!updateResultRsp.isValid()) {
            LOGGER.error("SbiSubnetNetModel update failed");
            throw new ServiceException("SbiSubnetNetModel update failed");
        }

        ResultRsp<SbiSubnetNetModel> updateNetModelResultRsp =
                (new ModelDataDao<SbiSubnetNetModel>()).update(subnetNetModel, "description");
        if(!updateNetModelResultRsp.isValid()) {
            LOGGER.error("SbiSubnetNetModel update in database failed");
            throw new ServiceException("SbiSubnetNetModel update in database failed");
        }

        subnetModel.setActionState(ActionStatus.NORMAL.getName());

        return (new ModelDataDao<NbiSubnetModel>()).update(subnetModel, "actionState,description");
    }

    private ResultRsp<SbiSubnetNetModel> queryBySubnetId(String subnetId) throws ServiceException {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("serviceSubnetId", Arrays.asList(subnetId));

        ResultRsp<List<SbiSubnetNetModel>> queryResultRsp =
                (new ModelDataDao<SbiSubnetNetModel>()).queryByFilter(SbiSubnetNetModel.class, filterMap, null);
        if(!queryResultRsp.isValid()) {
            LOGGER.error("SbiSubnetNetModel query failed");
            return new ResultRsp<>(ErrorCode.OVERLAYVPN_FAILED);
        }

        List<SbiSubnetNetModel> subnetNetModelList = queryResultRsp.getData();
        if(subnetNetModelList.isEmpty()) {
            return new ResultRsp<>(ErrorCode.OVERLAYVPN_SUCCESS);
        } else {
            return new ResultRsp<>(ErrorCode.OVERLAYVPN_SUCCESS, subnetNetModelList.get(0));
        }
    }

    private void fillPortUUids(NbiSubnetModel subnetModel) throws ServiceException {

        List<String> portNames = subnetModel.getPortNames();
        if(CollectionUtils.isEmpty(portNames)) {
            return;
        }

        // Query Local Network Element
        NetworkElementMO localCPE = siteModelDao.getSiteLocalCpe(subnetModel.getSiteId());

        List<LogicalTernminationPointMO> totalLtpMOList = new ArrayList<>();
        for(String portName : portNames) {
            Map<String, String> conditionMap = new HashMap<>();
            conditionMap.put("name", portName);
            conditionMap.put("meID", localCPE.getId());
            List<LogicalTernminationPointMO> ltpMOList = ltpInvDao.query(conditionMap);
            totalLtpMOList.addAll(ltpMOList);
        }

        @SuppressWarnings("unchecked")
        List<String> portUuids = new ArrayList<>(CollectionUtils.collect(totalLtpMOList, new Transformer() {

            @Override
            public Object transform(Object arg0) {
                return ((LogicalTernminationPointMO)arg0).getId();
            }
        }));

        subnetModel.setPorts(portUuids);
    }

    private SbiSubnetNetModel buildCreateSubnetNetModel(NbiSubnetModel subnetModel, NetworkElementMO siteGateway,
            boolean isIpv6) throws ServiceException {
        SbiSubnetNetModel subnetNetModel = new SbiSubnetNetModel();

        subnetNetModel.setUuid(UuidUtils.createUuid());

        subnetNetModel.setNeId(siteGateway.getId());
        subnetNetModel.setControllerId(siteGateway.getControllerID().get(0));

        subnetNetModel.setActionState(ActionStatus.CREATING.getName());

        subnetNetModel.setChangedMode("false");
        subnetNetModel.setDescription(subnetModel.getDescription());

        subnetNetModel.setName(subnetModel.getName());
        subnetNetModel.setNetworkId(subnetModel.getUuid());
        subnetNetModel.setServiceSubnetId(subnetModel.getUuid());
        subnetNetModel.setTenantId(subnetModel.getTenantId());
        subnetNetModel.setVni(subnetModel.getVni());
        subnetNetModel.setVlanId(subnetModel.getVlanId());

        subnetNetModel.setUseMode("terminal");

        if(isIpv6) {
            subnetNetModel.setIpv6Address(subnetModel.getIpv6Address());
            subnetNetModel.setPrefixLength(subnetModel.getPrefixLength());
            subnetNetModel.setDhcp6Mode("server");
            subnetNetModel.setDhcp6Enable("true");
            subnetNetModel.setGatewayIp(subnetModel.getGatewayIp());
        } else {
            subnetNetModel.setCidrIpAddress(IpUtils.getMinIpFromCIDR(subnetModel.getCidrBlock()));
            subnetNetModel.setCidrMask(IpUtils.getNetMaskFromCIDR(subnetModel.getCidrBlock()));
            subnetNetModel.setEnableDhcp(subnetModel.getEnableDhcp());
            if("true".equals(subnetModel.getEnableDhcp())) {
                subnetNetModel.setDhcpMode("server");
            }
            subnetNetModel.setGatewayIp(subnetModel.getGatewayIp());

            String templateName = siteModelDao.getTemplateName(subnetModel.getSiteId());
            subnetNetModel.setPriorDnsServer(templateSbiSrevice.getPriorDnsServerIp(templateName));
            subnetNetModel.setStandbyDnsServer(templateSbiSrevice.getStandbyDnsServer(templateName));

            // Fill Gateway Ip Address
            subnetNetModel.setIpRangeStartIp(subnetModel.getGatewayIp());
        }

        return subnetNetModel;
    }

    /**
     * Check subnet model parameter.<br>
     * 
     * @param subnetModel NbiSubnetModel need to check
     * @param isIpv6 whether is ipv6 protocol
     * @throws ServiceException when check failed
     * @since SDNO 0.5
     */
    private void checkSubnetModelParameter(NbiSubnetModel subnetModel, boolean isIpv6) throws ServiceException {
        if(isIpv6) {
            if(StringUtils.isBlank(subnetModel.getCidrBlock()) || StringUtils.isBlank(subnetModel.getGatewayIp())
                    || StringUtils.isBlank(subnetModel.getIpv6Address())
                    || StringUtils.isBlank(subnetModel.getPrefixLength())) {
                LOGGER.error("Subnet parameter is not complete, need to fill complete paramters");
                throw new ServiceException("Subnet parameter is not complete in Ipv6");
            }
        } else {
            if(StringUtils.isBlank(subnetModel.getCidrBlock())) {
                LOGGER.error("CidrBlock need to be filled in Ipv4");
                throw new ServiceException("CidrBlock need to be filled in Ipv4");
            }
        }
    }

}
